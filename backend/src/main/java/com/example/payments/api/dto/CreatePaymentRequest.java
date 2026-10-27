package com.example.payments.api.dto;

import com.example.payments.domain.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "Must be a positive decimal amount.")
        @Digits(integer = 17, fraction = 2, message = "Must have at most 17 integer and 2 fractional digits.")
        String amount,
        @NotNull Currency currency,
        @NotNull @Size(min = 1, max = 30) String customerReference) {
}
