package com.scanbite.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "service_requests", indexes = {
    @Index(name = "idx_service_requests_cafe_id", columnList = "cafe_id"),
    @Index(name = "idx_service_requests_status", columnList = "status")
})
public class ServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cafe_id", nullable = false)
    private Cafe cafe;

    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    private CafeTable table;

    @Column(nullable = false)
    private String requestType; // WATER, TISSUE_PAPER, EXTRA_PLATE, EXTRA_SPOON, EXTRA_ROTI, WAITER

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED

    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cafe getCafe() {
        return cafe;
    }

    public void setCafe(Cafe cafe) {
        this.cafe = cafe;
    }

    public CafeTable getTable() {
        return table;
    }

    public void setTable(CafeTable table) {
        this.table = table;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
