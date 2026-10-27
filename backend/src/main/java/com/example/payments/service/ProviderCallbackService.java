package com.example.payments.service;

import com.example.payments.persistence.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ProviderCallbackService {

    private final PaymentRepository paymentRepository;

    public ProviderCallbackService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void handleProviderUpdate(ProviderCallbackData providerCallbackData) {
        var payment = paymentRepository.findByProviderPaymentIdForUpdate(providerCallbackData.providerPaymentId())
                .orElseThrow(PaymentNotFoundException::new);
        payment.applyProviderUpdate(
                providerCallbackData.status(),
                ProviderStatusMapper.toPaymentStatus(providerCallbackData.status()),
                providerCallbackData.occurredAt());
    }

    public record ProviderCallbackData(String eventId, String providerPaymentId, String status, Instant occurredAt) {
    }
}
