package com.example.payments.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class CallbackTimestampDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(Instant.class, "Must be an ISO-8601 date-time string.");
        }
        String value = parser.getText();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw context.weirdStringException(value, Instant.class, "Must be an ISO-8601 date-time.");
        }
    }
}
