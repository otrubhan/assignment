package com.example.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key")
    private UUID id;

    @Column(name = "request_data", nullable = false, length = 128)
    private String requestData;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(UUID id, String requestData, UUID paymentId) {
        this.id = id;
        this.requestData = requestData;
        this.paymentId = paymentId;
    }

    public String getRequestData() {
        return requestData;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

}
