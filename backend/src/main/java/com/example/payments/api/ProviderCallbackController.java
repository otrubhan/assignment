package com.example.payments.api;

import com.example.payments.api.dto.ProviderCallbackRequest;
import com.example.payments.service.ProviderCallbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderCallbackController {

    private final ProviderCallbackService callbackService;

    public ProviderCallbackController(ProviderCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/provider-callbacks")
    @ResponseStatus(HttpStatus.OK)
    public void receiveProviderCallback(@Valid @RequestBody ProviderCallbackRequest request) {
        callbackService.handle(new ProviderCallbackService.Callback(
                request.eventId(), request.providerPaymentId(), request.status(), request.occurredAt()));
    }
}
