package com.n11.product.product;

import com.n11.product.category.Category;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name_tr", nullable = false)
    private String nameTr;

    @Column(name = "description_tr")
    private String descriptionTr;

    @Column(name = "price_gross", nullable = false)
    private BigDecimal priceGross;

    @Column(name = "kdv_rate", nullable = false)
    private BigDecimal kdvRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // image_urls TEXT[] — Hibernate 6.6 native array support via @JdbcTypeCode(SqlTypes.ARRAY).
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", columnDefinition = "TEXT[]")
    private String[] imageUrls;

    @Column(name = "seller_name", nullable = false)
    private String sellerName;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() { /* JPA */ }

    public Product(UUID id, String sku, String nameTr, String descriptionTr,
                   BigDecimal priceGross, BigDecimal kdvRate, Category category,
                   String[] imageUrls, String sellerName, String slug,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.sku = sku;
        this.nameTr = nameTr;
        this.descriptionTr = descriptionTr;
        this.priceGross = priceGross;
        this.kdvRate = kdvRate;
        this.category = category;
        this.imageUrls = imageUrls;
        this.sellerName = sellerName;
        this.slug = slug;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId()              { return id; }
    public String getSku()           { return sku; }
    public String getNameTr()        { return nameTr; }
    public String getDescriptionTr() { return descriptionTr; }
    public BigDecimal getPriceGross(){ return priceGross; }
    public BigDecimal getKdvRate()   { return kdvRate; }
    public Category getCategory()    { return category; }
    public String[] getImageUrls()   { return imageUrls; }
    public String getSellerName()    { return sellerName; }
    public String getSlug()          { return slug; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    // Domain mutators for update path:
    public void rename(String nameTr) { this.nameTr = nameTr; this.updatedAt = Instant.now(); }
    public void reprice(BigDecimal priceGross, BigDecimal kdvRate) {
        this.priceGross = priceGross;
        this.kdvRate = kdvRate;
        this.updatedAt = Instant.now();
    }
    public void setDescription(String descriptionTr) { this.descriptionTr = descriptionTr; this.updatedAt = Instant.now(); }
    public void setImageUrls(String[] imageUrls)     { this.imageUrls = imageUrls; this.updatedAt = Instant.now(); }
    public void setCategory(Category category)       { this.category = category; this.updatedAt = Instant.now(); }
    public void setSellerName(String sellerName)     { this.sellerName = sellerName; this.updatedAt = Instant.now(); }
}
