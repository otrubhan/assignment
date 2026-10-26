package com.example.payments.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderCallbackRequest(
        @NotNull @Size(min = 1) String eventId,
        @NotNull @Size(min = 1) String providerPaymentId,
        String status,
        @NotNull @JsonDeserialize(using = CallbackTimestampDeserializer.class) Instant occurredAt) {
}
