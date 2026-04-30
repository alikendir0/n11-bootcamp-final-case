package com.n11.infra.arch;

import com.rabbitmq.client.Channel;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * D-10 / Backlog 999.2 — Architecture-test gate: every @RabbitListener method MUST use the
 * canonical "AUTO ack mode + RejectAndDontRequeueRecoverer" shape:
 *  - declare a Spring AMQP Message parameter
 *  - NOT declare a com.rabbitmq.client.Channel parameter (which would imply MANUAL ack mode)
 *
 * <p>This gate prevents the silent regression class fixed in Phase 4 Plan 04-03 commit 2b61689.
 * If a future consumer legitimately needs MANUAL ack with Channel, this test must be updated
 * deliberately — that's the point of the gate.
 */
class AmqpAckModeArchTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.n11");

    @Test
    void rabbitListeners_useCanonicalAutoAckShape() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(RabbitListener.class)
            .should(notDeclareChannelParameter())
            .andShould(declareMessageParameter())
            .as("All @RabbitListener methods must use AUTO-ack shape: Spring AMQP Message parameter, no com.rabbitmq.client.Channel parameter (D-10 / Plan 04-03 regression gate)");
        rule.check(CLASSES);
    }

    private static ArchCondition<JavaMethod> notDeclareChannelParameter() {
        return new ArchCondition<>("not declare a com.rabbitmq.client.Channel parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasChannel = method.getRawParameterTypes().stream()
                    .anyMatch(t -> t.isAssignableTo(Channel.class));
                if (hasChannel) {
                    events.add(SimpleConditionEvent.violated(method,
                        method.getFullName() + " has Channel parameter — MANUAL ack mode regression risk (D-10 / 999.2)"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> declareMessageParameter() {
        return new ArchCondition<>("declare a Spring AMQP Message parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasMessage = method.getRawParameterTypes().stream()
                    .anyMatch(t -> t.isAssignableTo(Message.class));
                if (!hasMessage) {
                    events.add(SimpleConditionEvent.violated(method,
                        method.getFullName() + " has no Spring AMQP Message parameter — non-canonical listener shape"));
                }
            }
        };
    }
}
