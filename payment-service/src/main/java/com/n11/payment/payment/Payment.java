package com.n11.payment.payment;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code payment.payments} table (Phase 5 D-06 skeleton).
 *
 * <p>Phase 5: status is always COMPLETED (mock skeleton).
 * Phase 6: iyzico_payment_id populated by real Iyzico checkout; FAILED status
 * wired when Iyzico declines.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "iyzico_payment_id")
    private String iyzicoPaymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() { /* JPA */ }

    public Payment(UUID id, UUID orderId, BigDecimal amount, String currency,
                   PaymentStatus status, String iyzicoPaymentId) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.iyzicoPaymentId = iyzicoPaymentId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId()                  { return id; }
    public UUID getOrderId()             { return orderId; }
    public BigDecimal getAmount()        { return amount; }
    public String getCurrency()          { return currency; }
    public PaymentStatus getStatus()     { return status; }
    public String getIyzicoPaymentId()   { return iyzicoPaymentId; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
