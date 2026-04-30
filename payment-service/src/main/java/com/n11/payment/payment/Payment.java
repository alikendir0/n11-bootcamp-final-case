package com.n11.payment.payment;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code payment.payments} table (Phase 5 D-06 skeleton).
 *
 * <p>Phase 6 stores the durable hosted Checkout Form state needed for idempotent
 * callback handling and timeout compensation.
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

    @Column(name = "iyzico_token", unique = true)
    private String iyzicoToken;

    @Column(name = "payment_page_url", columnDefinition = "TEXT")
    private String paymentPageUrl;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

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
    public String getIyzicoToken()        { return iyzicoToken; }
    public String getPaymentPageUrl()     { return paymentPageUrl; }
    public String getFailureReason()      { return failureReason; }
    public String getFailureCode()        { return failureCode; }
    public Instant getExpiresAt()         { return expiresAt; }
    public Instant getCompletedAt()       { return completedAt; }
    public Instant getFailedAt()          { return failedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public void markPending(String token, String paymentPageUrl, Instant expiresAt) {
        this.status = PaymentStatus.PENDING;
        this.iyzicoToken = token;
        this.paymentPageUrl = paymentPageUrl;
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String iyzicoPaymentId) {
        this.status = PaymentStatus.COMPLETED;
        this.iyzicoPaymentId = iyzicoPaymentId;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void markFailed(String reason, String code) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.failureCode = code;
        this.failedAt = Instant.now();
        this.updatedAt = this.failedAt;
    }

    public void markTimedOut() {
        this.status = PaymentStatus.TIMED_OUT;
        this.failureReason = "PAYMENT_TIMEOUT";
        this.failedAt = Instant.now();
        this.updatedAt = this.failedAt;
    }
}
