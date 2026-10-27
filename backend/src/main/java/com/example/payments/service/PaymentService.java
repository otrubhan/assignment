package com.example.payments.service;

import com.example.payments.domain.*;
import com.example.payments.persistence.IdempotencyKeyRepository;
import com.example.payments.persistence.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentCreationTransaction paymentCreationTransaction;
    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public PaymentService(PaymentCreationTransaction paymentCreationTransaction,
            PaymentRepository paymentRepository, IdempotencyKeyRepository idempotencyKeyRepository) {
        this.paymentCreationTransaction = paymentCreationTransaction;
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    public CreatePaymentResponseData createPayment(UUID idempotencyKey, CreatePaymentData createPaymentData) {
        String requestData = createPaymentData.amount().setScale(2).toPlainString()
                + "|" + createPaymentData.currency().name()
                + "|" + createPaymentData.customerReference();
        try {
            return paymentCreationTransaction.create(idempotencyKey, requestData, createPaymentData);
        } catch (DataIntegrityViolationException exception) {
            return resolveConcurrentRequest(idempotencyKey, requestData, exception);
        }
    }

    @Transactional(readOnly = true)
    public PaymentDetails getByProviderPaymentId(String providerPaymentId) {
        var payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(PaymentNotFoundException::new);
        return toDetails(payment);
    }

    private CreatePaymentResponseData resolveConcurrentRequest(
            UUID idempotencyKey, String requestData, DataIntegrityViolationException originalException) {
        var existing = idempotencyKeyRepository.findById(idempotencyKey);
        if (existing.isEmpty()) {
            throw originalException;
        }
        IdempotencyKey record = existing.get();
        if (!record.getRequestData().equals(requestData)) {
            throw new IdempotencyConflictException();
        }
        var payment = paymentRepository.findById(record.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record references a missing payment.", originalException));
        return new CreatePaymentResponseData(payment.getProviderPaymentId());
    }

    private PaymentDetails toDetails(Payment payment) {
        return new PaymentDetails(
                payment.getId(), payment.getStatus(), payment.getProviderStatus(),
                payment.getProviderPaymentId(), payment.getLastCallbackOccurredAt(),
                payment.getAmount(), payment.getCurrency(), payment.getCustomerReference());
    }

    public record CreatePaymentData(BigDecimal amount, Currency currency, String customerReference) {
    }

    public record CreatePaymentResponseData(String providerPaymentId) {
    }

    public record PaymentDetails(UUID id, PaymentStatus status, String providerStatus,
                                 String providerPaymentId, Instant lastCallbackOccurredAt, BigDecimal amount,
                                 Currency currency, String customerReference) {
    }
}