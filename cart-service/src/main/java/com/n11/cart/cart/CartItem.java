package com.n11.cart.cart;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@IdClass(CartItemId.class)
public class CartItem {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "name_snapshot", nullable = false)
    private String nameSnapshot;

    @Column(name = "image_url_snapshot")
    private String imageUrlSnapshot;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() { /* JPA */ }

    public CartItem(UUID userId, UUID productId, int qty, BigDecimal unitPriceSnapshot,
                    String nameSnapshot, String imageUrlSnapshot) {
        this.userId = userId;
        this.productId = productId;
        this.qty = qty;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.nameSnapshot = nameSnapshot;
        this.imageUrlSnapshot = imageUrlSnapshot;
        Instant now = Instant.now();
        this.addedAt = now;
        this.updatedAt = now;
    }

    public UUID getUserId()              { return userId; }
    public UUID getProductId()           { return productId; }
    public int getQty()                  { return qty; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public String getNameSnapshot()      { return nameSnapshot; }
    public String getImageUrlSnapshot()  { return imageUrlSnapshot; }
    public Instant getAddedAt()          { return addedAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
