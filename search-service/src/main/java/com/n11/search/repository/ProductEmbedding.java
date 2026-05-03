package com.n11.search.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_embeddings")
public class ProductEmbedding {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    private float[] embedding;

    @Column(name = "name_tr")
    private String nameTr;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    protected ProductEmbedding() {}

    public ProductEmbedding(UUID productId, float[] embedding, String nameTr) {
        this.productId = productId;
        this.embedding = embedding;
        this.nameTr = nameTr;
        this.indexedAt = Instant.now();
    }

    public UUID getProductId() { return productId; }
    public float[] getEmbedding() { return embedding; }
    public String getNameTr() { return nameTr; }
    public Instant getIndexedAt() { return indexedAt; }
}
