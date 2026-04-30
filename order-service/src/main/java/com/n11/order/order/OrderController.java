package com.n11.order.order;

import com.n11.order.order.dto.CreateOrderRequest;
import com.n11.order.order.dto.OrderDetailDto;
import com.n11.order.order.dto.OrderListItemDto;
import com.n11.order.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order management and saga state tracking")
public class OrderController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final OrderCancellationService orderCancellationService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService,
                           OrderCancellationService orderCancellationService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
        this.orderCancellationService = orderCancellationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(HttpServletRequest req,
                                                @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                @Valid @RequestBody CreateOrderRequest body) {
        UUID userId = resolveUserId(req);
        OrderResponse resp = orderService.createOrder(userId, idempotencyKey, body);
        // First call → 202; replay (status past PENDING) → 200 (Stripe convention; D-05)
        HttpStatus status = (resp.status() == OrderStatus.PENDING) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resp);
    }

    @GetMapping
    public List<OrderListItemDto> list(HttpServletRequest req) {
        return orderQueryService.listForUser(resolveUserId(req));
    }

    @GetMapping("/{id}")
    public OrderDetailDto detail(HttpServletRequest req, @PathVariable UUID id) {
        return orderQueryService.getDetail(resolveUserId(req), id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(HttpServletRequest req, @PathVariable UUID id) {
        orderCancellationService.cancel(resolveUserId(req), id);
    }

    private UUID resolveUserId(HttpServletRequest req) {
        String h = req.getHeader(HEADER_USER_ID);
        if (h == null || h.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
        try { return UUID.fromString(h); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği"); }
    }
}
