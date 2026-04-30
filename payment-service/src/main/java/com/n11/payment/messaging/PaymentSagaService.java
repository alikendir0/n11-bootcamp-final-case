package com.n11.payment.messaging;

import com.n11.events.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * D-06 mock payment skeleton: consume stock.reserved → wait mock-delay → publish payment.completed.
 *
 * <p>v1 amount = stock.reserved.totalAmount (W4 closure — propagated verbatim from order.created
 * by inventory-service). Phase 6 swaps internals for real Iyzico without touching the topology.
 *
 * <p>NOT @Transactional — delegates to {@link PaymentTransactionalService} for the proxy boundary.
 * The mock delay happens BEFORE the transaction opens (Pitfall 1: don't sleep inside tx).
 */
@Service
public class PaymentSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentSagaService.class);

    private final PaymentTransactionalService paymentTransactionalService;
    private final long mockDelayMs;

    public PaymentSagaService(PaymentTransactionalService paymentTransactionalService,
                              @Value("${mock.payment.delay-ms:100}") long mockDelayMs) {
        this.paymentTransactionalService = paymentTransactionalService;
        this.mockDelayMs = mockDelayMs;
    }

    public void processStockReserved(UUID eventId, Envelope envelope,
                                     StockReservedConsumer.StockReservedPayload payload) {
        // Mock delay BEFORE @Transactional opens (Pitfall 1: don't sleep inside tx).
        if (mockDelayMs > 0) {
            try {
                Thread.sleep(mockDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        LOG.debug("payment.saga: processing stock.reserved event {} for order {}", eventId, payload.orderId());
        paymentTransactionalService.persistAndPublish(eventId, envelope, payload);
    }
}
