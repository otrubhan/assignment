package com.example.payments.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ProviderCallbackRequest(
        @NotNull @Size(min = 1, max = 255) String eventId,
        @NotNull @Size(min = 1) String providerPaymentId,
        String status,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
}
