package com.n11.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
    Optional<Payment> findByIyzicoToken(String token);

    @Query("select p from Payment p where p.status = 'PENDING' and p.expiresAt <= :now")
    List<Payment> findExpiredPendingPayments(Instant now);
}
