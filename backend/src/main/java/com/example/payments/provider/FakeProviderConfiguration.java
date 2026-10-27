package com.example.payments.provider;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FakeProviderConfiguration {

    @Bean
    FakeProviderScenario fakeProviderScenario(
            ObjectMapper objectMapper,
            @Value("${fake-provider.scenario}") Resource resource) {
        ObjectMapper scenarioMapper = objectMapper.copy();
        scenarioMapper.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature());
        try (InputStream input = resource.getInputStream()) {
            FakeProviderScenario scenario = scenarioMapper.readValue(input, FakeProviderScenario.class);
            if (scenario == null) {
                throw new IllegalArgumentException("Scenario must be a JSON object.");
            }
            return scenario;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid fake provider scenario: " + resource, exception);
        }
    }

    @Bean
    ThreadPoolTaskScheduler fakeProviderScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("fake-provider-");
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    @Bean
    RestClient fakeProviderClient(RestClient.Builder builder) {
        return builder.requestFactory(new SimpleClientHttpRequestFactory()).build();
    }

}
