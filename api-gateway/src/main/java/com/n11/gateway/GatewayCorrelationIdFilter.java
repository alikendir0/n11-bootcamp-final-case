package com.n11.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Reactive correlation-ID propagation filter (D-09).
 *
 * <p>Inbound: reads {@code X-Correlation-Id}; if absent or blank, generates a new UUID.
 * The forwarded request and the response both carry the (possibly newly minted)
 * correlation-ID. The value is also written to Reactor {@code Context} keyed
 * {@code "correlationId"} so downstream reactive code (and the Reactor automatic
 * context-propagation hook in Boot 3.5+) can lift it into MDC if/when this gateway
 * runs reactive logging.
 *
 * <p>Phase 1: this is the ONLY correlation wire on the gateway. The {@code common-logging}
 * servlet filter ({@code com.n11.logging.CorrelationIdFilter}) does not bind here because
 * the gateway is reactive; the two implementations stay in sync via the shared
 * {@code "X-Correlation-Id"} header name -- this constant must equal
 * {@code com.n11.logging.CorrelationIdFilter#HEADER} from 01-04.
 *
 * <p>The {@code GlobalFilter} import is at {@code org.springframework.cloud.gateway.filter}
 * -- in Northfields (Spring Cloud 2025.0 / spring-cloud-gateway-server-webflux 4.3.0) the
 * starter coordinate moved (`spring-cloud-starter-gateway-server-webflux`) and the
 * property prefix moved ({@code spring.cloud.gateway.server.webflux.*}), but the
 * {@code GlobalFilter} class package stayed the same. Verified by unzip of
 * spring-cloud-gateway-server-4.3.0.jar.
 */
@Component
public class GatewayCorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders().getFirst(HEADER);
        final String cid = (inbound == null || inbound.isBlank())
            ? UUID.randomUUID().toString()
            : inbound;

        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header(HEADER, cid)
            .build();

        // Echo on the response so clients can correlate in dev tools / logs.
        exchange.getResponse().getHeaders().set(HEADER, cid);

        return chain.filter(exchange.mutate().request(mutated).build())
            .contextWrite(Context.of(CONTEXT_KEY, cid));
    }

    @Override
    public int getOrder() {
        // Run BEFORE every other GlobalFilter so all subsequent filters see the
        // correlation-ID in the request headers and the Reactor Context.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
