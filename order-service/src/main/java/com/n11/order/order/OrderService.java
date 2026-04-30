package com.n11.order.order;

import com.n11.order.clients.AddressSnapshot;
import com.n11.order.clients.CartClient;
import com.n11.order.clients.CartView;
import com.n11.order.clients.IdentityClient;
import com.n11.order.clients.ProductClient;
import com.n11.order.clients.ProductSnapshot;
import com.n11.order.idempotency.OrderIdempotencyKey;
import com.n11.order.idempotency.OrderIdempotencyKeyRepository;
import com.n11.order.order.dto.CreateOrderRequest;
import com.n11.order.order.dto.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    private final CartClient cartClient;
    private final ProductClient productClient;
    private final IdentityClient identityClient;
    private final OrderIdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderTransactionalService orderTransactionalService;

    public OrderService(CartClient cartClient, ProductClient productClient, IdentityClient identityClient,
                        OrderIdempotencyKeyRepository idempotencyKeyRepository,
                        OrderTransactionalService orderTransactionalService) {
        this.cartClient = cartClient;
        this.productClient = productClient;
        this.identityClient = identityClient;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.orderTransactionalService = orderTransactionalService;
    }

    public OrderResponse createOrder(UUID userId, UUID idempotencyKey, CreateOrderRequest body) {
        if (!"CARD".equals(body.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu ödeme yöntemi henüz desteklenmiyor: " + body.paymentMethod());
        }

        // 1) Idempotency-Key dedup (Stripe pattern, D-05)
        Optional<OrderIdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId);
        if (existing.isPresent()) {
            UUID orderId = existing.get().getOrderId();
            return orderTransactionalService.toResponseFor(orderId, true);
        }
        // Cross-user collision detection (Pitfall 2)
        Optional<OrderIdempotencyKey> anyForKey = idempotencyKeyRepository.findFirstByIdempotencyKey(idempotencyKey);
        if (anyForKey.isPresent() && !anyForKey.get().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz idempotency anahtarı");
        }

        // 2) Sync REST calls — BEFORE @Transactional (RESEARCH Pitfall 1)
        CartView cart = cartClient.getCart(userId);
        if (cart.items() == null || cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sepet boş");
        }
        AddressSnapshot address = identityClient.getAddress(body.addressId(), userId);
        if (address.userId() != null && !address.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adres bulunamadı: " + body.addressId());
        }

        // 3) Price drift check (D-01 strict equality)
        Map<UUID, BigDecimal> currentPrices = new HashMap<>();
        for (CartView.Line line : cart.items()) {
            ProductSnapshot snap = productClient.fetchSnapshot(line.productId());
            currentPrices.put(line.productId(), snap.priceGross() != null ? snap.priceGross() : BigDecimal.ZERO);
        }
        List<UpdatedItem> drift = cart.items().stream()
            .filter(l -> currentPrices.get(l.productId()).compareTo(l.unitPriceSnapshot()) != 0)
            .map(l -> new UpdatedItem(l.productId(), currentPrices.get(l.productId()), l.unitPriceSnapshot()))
            .toList();
        if (!drift.isEmpty()) {
            throw new PriceDriftException(drift);
        }

        // 4) Persist in @Transactional (single tx — order + items + address + idempotency-key + outbox)
        return orderTransactionalService.persistOrder(userId, idempotencyKey, body, cart, address);
    }

    public record UpdatedItem(UUID productId, BigDecimal currentPrice, BigDecimal snapshotPrice) {}

    public static class PriceDriftException extends RuntimeException {
        private final List<UpdatedItem> updatedItems;
        public PriceDriftException(List<UpdatedItem> updatedItems) {
            super("Fiyatlar değişti: " + updatedItems.size() + " kalem");
            this.updatedItems = updatedItems;
        }
        public List<UpdatedItem> getUpdatedItems() { return updatedItems; }
    }
}
