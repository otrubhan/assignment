package com.example.payments.api.dto;

import java.util.Map;

public record ValidationErrorResponse(Map<String, String> fieldErrors) {
}
