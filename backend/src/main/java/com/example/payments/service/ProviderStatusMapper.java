package com.example.payments.service;

import com.example.payments.domain.PaymentStatus;
import com.example.payments.domain.ProviderPaymentStatus;

public final class ProviderStatusMapper {

    private ProviderStatusMapper() {
    }

    public static PaymentStatus toPaymentStatus(String providerStatus) {
        if (providerStatus == null) {
            return PaymentStatus.UNKNOWN;
        }
        ProviderPaymentStatus knownStatus;
        try {
            knownStatus = ProviderPaymentStatus.valueOf(providerStatus);
        } catch (IllegalArgumentException exception) {
            return PaymentStatus.UNKNOWN;
        }
        return switch (knownStatus) {
            case AUTHORIZED -> PaymentStatus.PENDING;
            case CAPTURED -> PaymentStatus.SUCCEEDED;
            case DECLINED -> PaymentStatus.FAILED;
            case ERROR -> PaymentStatus.UNKNOWN;
        };
    }
}
