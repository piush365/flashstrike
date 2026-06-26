package com.example.flashsale.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * @Data is intentionally avoided on JPA entities: it generates equals/hashCode over all
 * fields which breaks Hibernate's identity-based session cache, and toString can trigger
 * lazy-load proxies. Explicit getters/setters are used instead.
 */
@Entity
@Table(
    name = "orders",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_idempotency_key",
        columnNames = "idempotency_key"
    )
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(nullable = false, length = 20)
    private String status;

    // Prevents duplicate DB inserts when Kafka redelivers the same event.
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36, updatable = false)
    private String idempotencyKey;

    // Set by the database at insert time; prevents Java clock-skew issues.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Order() {}

    public Long getId()                  { return id; }
    public String getUserId()            { return userId; }
    public void setUserId(String v)      { this.userId = v; }
    public String getProductId()         { return productId; }
    public void setProductId(String v)   { this.productId = v; }
    public String getStatus()            { return status; }
    public void setStatus(String v)      { this.status = v; }
    public String getIdempotencyKey()    { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public Instant getCreatedAt()        { return createdAt; }
}
