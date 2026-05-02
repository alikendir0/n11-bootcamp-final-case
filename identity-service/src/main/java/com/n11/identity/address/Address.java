package com.n11.identity.address;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "il", nullable = false, length = 50)
    private String il;

    @Column(name = "ilce", nullable = false, length = 80)
    private String ilce;

    @Column(name = "mahalle", length = 120)
    private String mahalle;       // nullable per V2 DDL

    @Column(name = "street_line", nullable = false, length = 255)
    private String streetLine;

    @Column(name = "postal_code", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Address() { /* JPA */ }

    public Address(UUID id, UUID userId, String title, String recipientName, String phone,
                   String il, String ilce, String mahalle, String streetLine,
                   String postalCode, boolean isDefault, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.recipientName = recipientName;
        this.phone = phone;
        this.il = il;
        this.ilce = ilce;
        this.mahalle = mahalle;
        this.streetLine = streetLine;
        this.postalCode = postalCode;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
    }

    public UUID getId()             { return id; }
    public UUID getUserId()         { return userId; }
    public String getTitle()        { return title; }
    public String getRecipientName(){ return recipientName; }
    public String getPhone()        { return phone; }
    public String getIl()           { return il; }
    public String getIlce()         { return ilce; }
    public String getMahalle()      { return mahalle; }
    public String getStreetLine()   { return streetLine; }
    public String getPostalCode()   { return postalCode; }
    public boolean isDefault()      { return isDefault; }
    public Instant getCreatedAt()   { return createdAt; }

    public void update(String title, String recipientName, String phone, String il, String ilce,
                       String mahalle, String streetLine, String postalCode, boolean isDefault) {
        this.title = title;
        this.recipientName = recipientName;
        this.phone = phone;
        this.il = il;
        this.ilce = ilce;
        this.mahalle = mahalle;
        this.streetLine = streetLine;
        this.postalCode = postalCode;
        this.isDefault = isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
