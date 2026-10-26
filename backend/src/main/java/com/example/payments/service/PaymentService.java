package com.example.payments.service;

import com.example.payments.domain.Currency;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.domain.ProviderPaymentStatus;
import com.example.payments.provider.FakeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final FakeProvider fakeProvider;

    public PaymentService(FakeProvider fakeProvider) {
        this.fakeProvider = fakeProvider;
    }

    @Transactional
    public CreatePaymentResponseData createPayment(UUID idempotencyKey, CreatePaymentData createPaymentData) {
        //TODO: handle deduplication
        String providerPaymentId = fakeProvider.authorize();
        //TODO: save payment details in database (PENDING, AUTHORIZED, providerPaymentId, last_occured_at = null, amount, currency, customerReference) lock?
        return new CreatePaymentResponseData(providerPaymentId);
    }

    public PaymentDetails getByProviderPaymentId(String providerPaymentId) {
        // retrieve payment details from database by providerPaymentId or else throw PaymentNotFoundException
        PaymentDetails result = new PaymentDetails(UUID.randomUUID(), PaymentStatus.PENDING, ProviderPaymentStatus.CAPTURED, providerPaymentId, Instant.now(), new BigDecimal("100.00"), Currency.USD, "customer-123");
        return result;
    }

    public record CreatePaymentData(BigDecimal amount, Currency currency, String customerReference) {
    }

    public record CreatePaymentResponseData(String providerPaymentId) {
    }

    public record PaymentDetails(UUID id, PaymentStatus status, ProviderPaymentStatus providerStatus,
                                 String providerPaymentId, Instant lastCallbackOccurredAt, BigDecimal amount,
                                 Currency currency, String customerReference) {
    }
}