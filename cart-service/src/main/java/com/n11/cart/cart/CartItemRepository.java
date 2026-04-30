package com.n11.cart.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    /**
     * D-02 UPSERT: same productId twice → single row with summed qty.
     * Schema-qualified table name so Testcontainers slice tests resolve the
     * correct table even when search_path is not set (Phase 4 Plan 04-01 lesson).
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO cart.cart_items
            (user_id, product_id, qty, unit_price_snapshot, name_snapshot, image_url_snapshot, added_at, updated_at)
        VALUES
            (:userId, :productId, :qty, :unitPriceSnapshot, :nameSnapshot, :imageUrlSnapshot, now(), now())
        ON CONFLICT (user_id, product_id) DO UPDATE SET
            qty                  = cart.cart_items.qty + EXCLUDED.qty,
            unit_price_snapshot  = EXCLUDED.unit_price_snapshot,
            name_snapshot        = EXCLUDED.name_snapshot,
            image_url_snapshot   = EXCLUDED.image_url_snapshot,
            updated_at           = now()
        """, nativeQuery = true)
    int upsertAddQty(@Param("userId") UUID userId,
                     @Param("productId") UUID productId,
                     @Param("qty") int qty,
                     @Param("unitPriceSnapshot") BigDecimal unitPriceSnapshot,
                     @Param("nameSnapshot") String nameSnapshot,
                     @Param("imageUrlSnapshot") String imageUrlSnapshot);

    /**
     * PATCH /cart/items/{productId} — REPLACE qty (do NOT add). Re-fetches snapshot via service layer.
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE cart.cart_items SET qty = :qty,
            unit_price_snapshot = :unitPriceSnapshot,
            name_snapshot = :nameSnapshot,
            image_url_snapshot = :imageUrlSnapshot,
            updated_at = now()
        WHERE user_id = :userId AND product_id = :productId
        """, nativeQuery = true)
    int updateLine(@Param("userId") UUID userId,
                   @Param("productId") UUID productId,
                   @Param("qty") int qty,
                   @Param("unitPriceSnapshot") BigDecimal unitPriceSnapshot,
                   @Param("nameSnapshot") String nameSnapshot,
                   @Param("imageUrlSnapshot") String imageUrlSnapshot);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cart.cart_items WHERE user_id = :userId AND product_id = :productId",
           nativeQuery = true)
    int deleteLine(@Param("userId") UUID userId, @Param("productId") UUID productId);

    /**
     * D-07 cart-clear on order.confirmed.
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cart.cart_items WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM cart.cart_items WHERE user_id = :userId ORDER BY added_at ASC",
           nativeQuery = true)
    List<CartItem> findByUserIdOrderByAddedAt(@Param("userId") UUID userId);
}
