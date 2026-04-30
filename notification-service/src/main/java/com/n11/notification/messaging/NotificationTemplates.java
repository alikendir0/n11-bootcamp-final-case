package com.n11.notification.messaging;

import com.n11.notification.messaging.payloads.OrderCancelledPayload;
import com.n11.notification.messaging.payloads.OrderConfirmedPayload;
import com.n11.notification.messaging.payloads.PaymentFailedPayload;
import com.n11.notification.messaging.payloads.UserRegisteredPayload;

/**
 * Static Turkish subject and body templates for the 4 notification event types.
 *
 * <p>All body language is Turkish per CLAUDE.md (LOC-01 family of requirements).
 * All code identifiers, method names, and log keys remain in English.
 *
 * <p>Design choice (RESEARCH.md Pattern 5): inline String.format templates for v1 —
 * no Freemarker or Thymeleaf dependency required at this scale.
 * Templates can be extracted to message files later without changing the interface.
 */
public final class NotificationTemplates {

    private NotificationTemplates() { /* utility class — no instances */ }

    // ---- Subject constants (Turkish) ----

    public static final String SUBJECT_ORDER_CONFIRMED = "Siparişiniz onaylandı";
    public static final String SUBJECT_ORDER_CANCELLED = "Siparişiniz iptal edildi";
    public static final String SUBJECT_PAYMENT_FAILED  = "Ödemeniz alınamadı";
    public static final String SUBJECT_USER_REGISTERED = "Hoş geldiniz!";

    // ---- Body methods (Turkish) ----

    /**
     * Builds the Turkish notification body for an order.confirmed event.
     * Example: "Merhaba, 550e8400-e29b... siparişiniz onaylandı. Toplam tutar: 199,99 TL."
     */
    public static String bodyOrderConfirmed(OrderConfirmedPayload p) {
        return String.format(
            "Merhaba, %s siparişiniz onaylandı. Toplam tutar: %.2f TL.",
            p.orderId(), p.totalAmount());
    }

    /**
     * Builds the Turkish notification body for an order.cancelled event.
     * Reason code is mapped to Turkish via {@link #reasonToTurkish(String)}.
     * Example: "Merhaba, 550e8400... siparişiniz iptal edildi. Sebep: Stok yetersizliği."
     */
    public static String bodyOrderCancelled(OrderCancelledPayload p) {
        return String.format(
            "Merhaba, %s siparişiniz iptal edildi. Sebep: %s.",
            p.orderId(), reasonToTurkish(p.reason()));
    }

    /**
     * Builds the Turkish notification body for a payment.failed event.
     * Note: schema has no userId — orderId is used as the identifier in the message.
     * Example: "Merhaba, 550e8400... siparişinize ait ödeme alınamadı. Lütfen tekrar deneyin."
     */
    public static String bodyPaymentFailed(PaymentFailedPayload p) {
        return String.format(
            "Merhaba, %s siparişinize ait ödeme alınamadı. Lütfen tekrar deneyin.",
            p.orderId());
    }

    /**
     * Builds the Turkish welcome body for a user.registered event.
     * Example: "Merhaba Ali Yılmaz, n11'e hoş geldiniz. Üyeliğiniz başarıyla oluşturuldu."
     */
    public static String bodyUserRegistered(UserRegisteredPayload p) {
        return String.format(
            "Merhaba %s, n11'e hoş geldiniz. Üyeliğiniz başarıyla oluşturuldu.",
            p.fullName());
    }

    // ---- Reason code → Turkish mapping (order.cancelled) ----

    /**
     * Maps order cancellation reason codes (English) to Turkish display strings.
     * Unknown codes are passed through unchanged.
     */
    static String reasonToTurkish(String reason) {
        if (reason == null) return "Bilinmeyen sebep";
        return switch (reason) {
            case "OUT_OF_STOCK"     -> "Stok yetersizliği";
            case "PAYMENT_DECLINED" -> "Ödeme reddedildi";
            case "USER_CANCELLED"   -> "Sipariş iptal edildi";
            case "PAYMENT_TIMEOUT"  -> "Ödeme süresi doldu";
            default                 -> reason;  // pass through unknown codes
        };
    }
}
