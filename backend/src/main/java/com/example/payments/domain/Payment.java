package com.example.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "customer_reference", nullable = false, length = 30)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_status", length = 64)
    private String providerStatus;

    @Column(name = "provider_payment_id", nullable = false, unique = true, length = 64)
    private String providerPaymentId;

    @Column(name = "last_callback_occurred_at")
    private Instant lastCallbackOccurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Payment() {
    }

    public Payment(UUID id, BigDecimal amount, Currency currency, String customerReference,
            PaymentStatus status, String providerPaymentId) {
        this.id = id;
        this.amount = amount;
        this.currency = currency;
        this.customerReference = customerReference;
        this.status = status;
        this.providerPaymentId = providerPaymentId;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public boolean applyProviderUpdate(String providerStatus, PaymentStatus newStatus, Instant occurredAt) {
        if (lastCallbackOccurredAt != null && !occurredAt.isAfter(lastCallbackOccurredAt)) {
            return false;
        }
        if (newStatus == PaymentStatus.UNKNOWN
                && (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED)) {
            return false;
        }

        this.providerStatus = providerStatus;
        this.status = newStatus;
        this.lastCallbackOccurredAt = occurredAt;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public Instant getLastCallbackOccurredAt() {
        return lastCallbackOccurredAt;
    }
}
