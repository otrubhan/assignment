package com.example.payments.service;

import com.example.payments.domain.IdempotencyKey;
import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.domain.ProviderPaymentStatus;
import com.example.payments.persistence.IdempotencyKeyRepository;
import com.example.payments.persistence.PaymentRepository;
import com.example.payments.provider.FakeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentCreationTransaction {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final FakeProvider fakeProvider;

    public PaymentCreationTransaction(PaymentRepository paymentRepository,
            IdempotencyKeyRepository idempotencyKeyRepository, FakeProvider fakeProvider) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.fakeProvider = fakeProvider;
    }

    @Transactional
    public PaymentService.CreatePaymentResponseData create(
            UUID idempotencyKey, String requestData, PaymentService.CreatePaymentData command) {
        var existing = idempotencyKeyRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.getRequestData().equals(requestData)) {
                throw new IdempotencyConflictException();
            }
            return resultFor(record.getPaymentId());
        }

        String providerPaymentId = fakeProvider.authorize();
        Payment payment = new Payment(
                UUID.randomUUID(),
                command.amount(),
                command.currency(),
                command.customerReference(),
                PaymentStatus.PENDING,
                providerPaymentId,
                ProviderPaymentStatus.AUTHORIZED.toString());
        paymentRepository.saveAndFlush(payment);

        var idempotencyRecord = new IdempotencyKey(
                idempotencyKey,
                requestData,
                payment.getId());
        idempotencyKeyRepository.save(idempotencyRecord);
        return new PaymentService.CreatePaymentResponseData(providerPaymentId);
    }

    private PaymentService.CreatePaymentResponseData resultFor(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Idempotency record references a missing payment."));
        return new PaymentService.CreatePaymentResponseData(payment.getProviderPaymentId());
    }
}
