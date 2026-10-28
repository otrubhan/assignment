package com.example.payments.service;

import com.example.payments.domain.ProcessedCallbackEvent;
import com.example.payments.persistence.PaymentRepository;
import com.example.payments.persistence.ProcessedCallbackEventRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallbackTransactionProcessor {

    private final PaymentRepository paymentRepository;
    private final ProcessedCallbackEventRepository eventRepository;
    private final EntityManager entityManager;

    public CallbackTransactionProcessor(PaymentRepository paymentRepository,
            ProcessedCallbackEventRepository eventRepository, EntityManager entityManager) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(ProviderCallbackService.ProviderCallbackData callback) {
        var payment = paymentRepository.findByProviderPaymentIdForUpdate(callback.providerPaymentId())
                .orElseThrow(PaymentNotFoundException::new);
        if (eventRepository.existsById(callback.eventId())) {
            return;
        }

        entityManager.persist(new ProcessedCallbackEvent(callback.eventId(), payment.getId()));
        payment.applyProviderUpdate(
                callback.status(),
                ProviderStatusMapper.toPaymentStatus(callback.status()),
                callback.occurredAt());
    }
}
