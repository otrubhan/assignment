package com.example.payments.persistence;

import com.example.payments.domain.ProcessedCallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCallbackEventRepository extends JpaRepository<ProcessedCallbackEvent, String> {
}
