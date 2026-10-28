package com.example.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "processed_callback_events")
public class ProcessedCallbackEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    protected ProcessedCallbackEvent() {
    }

    public ProcessedCallbackEvent(String eventId, UUID paymentId) {
        this.eventId = eventId;
        this.paymentId = paymentId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
