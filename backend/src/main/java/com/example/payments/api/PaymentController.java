package com.example.payments.api;

import com.example.payments.api.dto.CreatePaymentRequest;
import com.example.payments.api.dto.CreatePaymentResponse;
import com.example.payments.api.dto.PaymentDetailsResponse;
import com.example.payments.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse createPayment(
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                    message = "Must be a UUID.")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        var result = paymentService.create(
                UUID.fromString(idempotencyKey),
                new PaymentService.CreatePayment(
                        new BigDecimal(request.amount()), request.currency(), request.customerReference()));
        return new CreatePaymentResponse(result.providerPaymentId());
    }

    @GetMapping("/{id}")
    public PaymentDetailsResponse getPayment(@PathVariable("id") String providerPaymentId) {
        var payment = paymentService.getByProviderPaymentId(providerPaymentId);
        return new PaymentDetailsResponse(
                payment.id(), payment.status(), payment.providerStatus(), payment.providerPaymentId(),
                payment.lastCallbackOccurredAt(), payment.amount().toPlainString(),
                payment.currency(), payment.customerReference());
    }
}
