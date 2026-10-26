package com.example.payments.service;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException() {
        super("Payment not found.");
    }
}
