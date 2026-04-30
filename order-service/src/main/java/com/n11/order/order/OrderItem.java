package com.n11.order.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "order_items") @IdClass(OrderItemId.class)
public class OrderItem {
    @Id @Column(name = "order_id") private UUID orderId;
    @Id @Column(name = "product_id") private UUID productId;
    @Column(name = "name_snapshot", nullable = false) private String nameSnapshot;
    @Column(name = "qty", nullable = false) private int qty;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2) private BigDecimal unitPrice;
    protected OrderItem() {}
    public OrderItem(UUID orderId, UUID productId, String nameSnapshot, int qty, BigDecimal unitPrice) {
        this.orderId = orderId; this.productId = productId; this.nameSnapshot = nameSnapshot;
        this.qty = qty; this.unitPrice = unitPrice;
    }
    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public String getNameSnapshot() { return nameSnapshot; }
    public int getQty() { return qty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
