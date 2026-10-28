package com.example.payments.service;

import com.example.payments.api.ApiExceptionHandler;
import com.example.payments.api.ProviderCallbackController;
import com.example.payments.domain.Currency;
import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.persistence.PaymentRepository;
import com.example.payments.persistence.ProcessedCallbackEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({ProviderCallbackService.class, CallbackTransactionProcessor.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderCallbackDeduplicationTest {

    private static final Instant TIME = Instant.parse("2026-10-28T10:00:00Z");

    @Autowired
    private ProviderCallbackService service;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private ProcessedCallbackEventRepository events;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        mvc = MockMvcBuilders.standaloneSetup(new ProviderCallbackController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    void duplicateEventCannotChangeStatusEvenWithLaterTimestamp(long seconds) {
        Payment payment = createPayment();
        handle("duplicate", payment, "AUTHORIZED", TIME);
        handle("duplicate", payment, "CAPTURED", TIME.plusSeconds(seconds));

        assertState(payment, PaymentStatus.PENDING, "AUTHORIZED", TIME);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void eventIdIsGlobalAcrossPayments() {
        Payment first = createPayment();
        Payment second = createPayment();
        handle("global", first, "ERROR", TIME);
        handle("global", second, "CAPTURED", TIME.plusSeconds(10));

        assertState(first, PaymentStatus.UNKNOWN, "ERROR", TIME);
        assertState(second, PaymentStatus.PENDING, "AUTHORIZED", null);
        assertThat(events.findById("global").orElseThrow().getPaymentId()).isEqualTo(first.getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {-10, 0})
    void suppressedTimestampStillRecordsEvent(long seconds) {
        Payment payment = createPayment();
        handle("initial", payment, "AUTHORIZED", TIME);
        handle("stale", payment, "CAPTURED", TIME.plusSeconds(seconds));
        handle("stale", payment, "CAPTURED", TIME.plusSeconds(20));

        assertState(payment, PaymentStatus.PENDING, "AUTHORIZED", TIME);
        assertThat(events.existsById("stale")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAPTURED", "DECLINED"})
    void finalStatusSuppressionRecordsEventWithoutChangingMetadata(String providerStatus) {
        Payment payment = createPayment();
        handle("final", payment, providerStatus, TIME);
        handle("suppressed", payment, "ERROR", TIME.plusSeconds(10));

        assertState(payment, ProviderStatusMapper.toPaymentStatus(providerStatus), providerStatus, TIME);
        assertThat(events.existsById("suppressed")).isTrue();
    }

    @Test
    void unknownPaymentDoesNotConsumeEventId() {
        var callback = new ProviderCallbackService.ProviderCallbackData(
                "unknown-target", "missing", "CAPTURED", TIME);
        assertThatThrownBy(() -> service.handleProviderUpdate(callback))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(events.existsById("unknown-target")).isFalse();

        Payment payment = createPayment();
        handle("unknown-target", payment, "CAPTURED", TIME);
        assertState(payment, PaymentStatus.SUCCEEDED, "CAPTURED", TIME);
    }

    @Test
    void failedCommitRollsBackEventAndPaymentTogether() {
        Payment payment = createPayment();
        // Exceed the stored provider-status limit to force a transaction failure.
        assertThatThrownBy(() -> handle("retryable", payment, "X".repeat(65), TIME))
                .isInstanceOf(RuntimeException.class);

        assertThat(events.existsById("retryable")).isFalse();
        assertState(payment, PaymentStatus.PENDING, "AUTHORIZED", null);

        handle("retryable", payment, "CAPTURED", TIME);
        assertState(payment, PaymentStatus.SUCCEEDED, "CAPTURED", TIME);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentDuplicateDeliveriesCommitOnlyOneEvent(boolean samePayment) throws Exception {
        Payment first = createPayment();
        Payment second = samePayment ? first : createPayment();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                awaitStart(ready, start);
                handle("concurrent", first, "ERROR", TIME);
            });
            var b = executor.submit(() -> {
                awaitStart(ready, start);
                handle("concurrent", second, "AUTHORIZED", TIME.plusSeconds(10));
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }

        assertThat(events.count()).isEqualTo(1);
        UUID winner = events.findById("concurrent").orElseThrow().getPaymentId();
        Payment storedFirst = reload(first);
        Payment storedSecond = reload(second);
        assertThat(winner).isIn(first.getId(), second.getId());
        if (samePayment) {
            assertThat(storedFirst.getLastCallbackOccurredAt()).isIn(TIME, TIME.plusSeconds(10));
        } else {
            Payment loser = winner.equals(first.getId()) ? storedSecond : storedFirst;
            assertThat(loser.getLastCallbackOccurredAt()).isNull();
            Payment applied = winner.equals(first.getId()) ? storedFirst : storedSecond;
            assertThat(applied.getLastCallbackOccurredAt())
                    .isEqualTo(winner.equals(first.getId()) ? TIME : TIME.plusSeconds(10));
        }
        handle("concurrent", first, "CAPTURED", TIME.plusSeconds(30));
        assertThat(reload(first).getLastCallbackOccurredAt()).isEqualTo(storedFirst.getLastCallbackOccurredAt());
    }

    @Test
    void duplicateCallbackIsAcknowledgedWith200() throws Exception {
        Payment payment = createPayment();
        mvc.perform(post("/provider-callbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("http-event", payment.getProviderPaymentId(), "AUTHORIZED")))
                .andExpect(status().isOk());
        mvc.perform(post("/provider-callbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("http-event", payment.getProviderPaymentId(), "CAPTURED")))
                .andExpect(status().isOk());
        assertState(payment, PaymentStatus.PENDING, "AUTHORIZED", TIME);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void unknownPaymentReturns404EvenForRecordedEventId() throws Exception {
        handle("recorded", createPayment(), "ERROR", TIME);
        mvc.perform(post("/provider-callbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("recorded", "missing", "CAPTURED")))
                .andExpect(status().isNotFound());
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void eventIdLengthIsValidatedBeforeProcessing() throws Exception {
        Payment payment = createPayment();
        for (String invalidId : new String[] {"", "x".repeat(256)}) {
            mvc.perform(post("/provider-callbacks").contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalidId, payment.getProviderPaymentId(), "ERROR")))
                    .andExpect(status().isBadRequest());
        }
        assertThat(events.count()).isZero();
        String maxLengthId = "x".repeat(255);
        mvc.perform(post("/provider-callbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(json(maxLengthId, payment.getProviderPaymentId(), "ERROR")))
                .andExpect(status().isOk());
        assertThat(events.existsById(maxLengthId)).isTrue();
    }

    private Payment createPayment() {
        return payments.saveAndFlush(new Payment(
                UUID.randomUUID(), new BigDecimal("12.30"), Currency.EUR, "customer",
                PaymentStatus.PENDING, UUID.randomUUID().toString(), "AUTHORIZED"));
    }

    private void handle(String eventId, Payment payment, String status, Instant occurredAt) {
        service.handleProviderUpdate(new ProviderCallbackService.ProviderCallbackData(
                eventId, payment.getProviderPaymentId(), status, occurredAt));
    }

    private Payment reload(Payment payment) {
        return payments.findById(payment.getId()).orElseThrow();
    }

    private void assertState(Payment payment, PaymentStatus status, String providerStatus, Instant timestamp) {
        Payment stored = reload(payment);
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(stored.getProviderStatus()).isEqualTo(providerStatus);
        assertThat(stored.getLastCallbackOccurredAt()).isEqualTo(timestamp);
    }

    private static String json(String eventId, String providerPaymentId, String status) {
        return """
                {"eventId":"%s","providerPaymentId":"%s","status":"%s","occurredAt":"%s"}
                """.formatted(eventId, providerPaymentId, status, TIME);
    }

    private static void awaitStart(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent callback start timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent callback interrupted.", exception);
        }
    }
}
