package com.example.payments.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class FakeProvider {

    private final FakeProviderScenario scenario;
    private final ThreadPoolTaskScheduler scheduler;
    private final RestClient client;
    private final URI callbackUrl;

    public FakeProvider(FakeProviderScenario scenario, ThreadPoolTaskScheduler fakeProviderScheduler,
            RestClient fakeProviderClient, @Value("${fake-provider.callback-url}") URI callbackUrl) {
        this.scenario = scenario;
        this.scheduler = fakeProviderScheduler;
        this.client = fakeProviderClient;
        this.callbackUrl = callbackUrl;
    }

    public String authorize() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("FakeProvider.authorize() requires an active payment transaction.");
        }
        String providerPaymentId = UUID.randomUUID().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                schedule(providerPaymentId, 0);
            }
        });
        return providerPaymentId;
    }

    private void schedule(String providerPaymentId, int index) {
        long delayMillis = ThreadLocalRandom.current().nextLong(2000, 5001);
        scheduler.schedule(() -> send(providerPaymentId, index), Instant.now().plusMillis(delayMillis));
    }

    private void send(String providerPaymentId, int index) {
        var entry = scenario.callbacks().get(index);
        String eventId = entry.eventId() == null
                ? UUID.randomUUID().toString()
                : providerPaymentId + ":" + entry.eventId();
        var body = new CallbackBody(
                eventId, providerPaymentId, entry.status(),
                entry.occurredAt() == null ? Instant.now() : entry.occurredAt());
        try {
            client.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("Delivered callback eventId=" + eventId + " providerPaymentId=" + providerPaymentId);
        } catch (RestClientException exception) {
            System.err.println("Callback delivery failed eventId=" + eventId + " providerPaymentId=" + providerPaymentId);
        }
        if (index + 1 < scenario.callbacks().size()) {
            schedule(providerPaymentId, index + 1);
        }
    }

    private record CallbackBody(String eventId, String providerPaymentId, String status, Instant occurredAt) {
    }
}
