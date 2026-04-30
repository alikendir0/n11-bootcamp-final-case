package com.n11.order.order;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "order_shipping_addresses")
public class OrderShippingAddress {
    @Id @Column(name = "order_id") private UUID orderId;
    @Column(name = "recipient_name", nullable = false) private String recipientName;
    @Column(name = "phone", nullable = false) private String phone;
    @Column(name = "il", nullable = false) private String il;
    @Column(name = "ilce", nullable = false) private String ilce;
    @Column(name = "mahalle", nullable = false) private String mahalle;
    @Column(name = "street_line", nullable = false) private String streetLine;
    @Column(name = "postal_code") private String postalCode;
    @Column(name = "title") private String title;
    protected OrderShippingAddress() {}
    public OrderShippingAddress(UUID orderId, String recipientName, String phone, String il, String ilce,
                                String mahalle, String streetLine, String postalCode, String title) {
        this.orderId = orderId; this.recipientName = recipientName; this.phone = phone;
        this.il = il; this.ilce = ilce; this.mahalle = mahalle; this.streetLine = streetLine;
        this.postalCode = postalCode; this.title = title;
    }
    public UUID getOrderId() { return orderId; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getIl() { return il; }
    public String getIlce() { return ilce; }
    public String getMahalle() { return mahalle; }
    public String getStreetLine() { return streetLine; }
    public String getPostalCode() { return postalCode; }
    public String getTitle() { return title; }
}
