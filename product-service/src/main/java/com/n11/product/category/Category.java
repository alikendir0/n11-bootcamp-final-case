package com.n11.product.category;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "name_tr", nullable = false)
    private String nameTr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() { /* JPA */ }

    public Category(UUID id, String slug, String nameTr, Category parent, int sortOrder) {
        this.id = id;
        this.slug = slug;
        this.nameTr = nameTr;
        this.parent = parent;
        this.sortOrder = sortOrder;
    }

    public UUID getId()         { return id; }
    public String getSlug()     { return slug; }
    public String getNameTr()   { return nameTr; }
    public Category getParent() { return parent; }
    public int getSortOrder()   { return sortOrder; }
}
