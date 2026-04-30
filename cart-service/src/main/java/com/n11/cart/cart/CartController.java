package com.n11.cart.cart;

import com.n11.cart.cart.dto.AddCartItemRequest;
import com.n11.cart.cart.dto.CartView;
import com.n11.cart.cart.dto.UpdateCartItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Cart REST surface (api-contracts.md §1):
 *   GET  /cart            — return current cart (lazy-create if no cart exists)
 *   POST /cart/items      — upsert {productId, qty}
 *   PATCH /cart/items/{productId} — update qty (re-fetches product snapshot per D-01)
 *   DELETE /cart/items/{productId} — remove line item
 *
 * <p>Gateway strips Authorization and injects X-User-Id (Phase 3 D-15).
 * cart-service NEVER decodes JWT.
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final CartService cartService;

    public CartController(CartService cartService) { this.cartService = cartService; }

    @GetMapping
    public CartView get(HttpServletRequest req) {
        return cartService.getCart(resolveUserId(req));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartView add(HttpServletRequest req,
                        @Valid @RequestBody AddCartItemRequest body) {
        return cartService.addItem(resolveUserId(req), body.productId(), body.qty());
    }

    @PatchMapping("/items/{productId}")
    public CartView update(HttpServletRequest req,
                           @PathVariable UUID productId,
                           @Valid @RequestBody UpdateCartItemRequest body) {
        return cartService.updateQty(resolveUserId(req), productId, body.qty());
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(HttpServletRequest req, @PathVariable UUID productId) {
        cartService.removeItem(resolveUserId(req), productId);
    }

    private UUID resolveUserId(HttpServletRequest req) {
        String h = req.getHeader(HEADER_USER_ID);
        if (h == null || h.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
        }
        try {
            return UUID.fromString(h);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
        }
    }
}
