package com.slotlock.outbox.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.ApiException;
import com.slotlock.outbox.entity.OutboxEvent;
import com.slotlock.outbox.enums.OutboxStatus;
import com.slotlock.outbox.repository.OutboxRepository;
import com.slotlock.outbox.service.OutboxService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DefaultOutboxService implements OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DefaultOutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // No explicit @Transactional here: called from inside book()/bookOptimistic()/cancel(),
    // each already @Transactional, so this save joins that SAME transaction via Spring's default
    // REQUIRED propagation. The outbox row and the business change either both commit or neither
    // does — that's the entire point of the pattern.
    @Override
    public void recordEvent(String aggregateType, Long aggregateId, String eventType, Object payload) {
        String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCodeEnum.INTERNAL_ERROR,
                    "Failed to serialize outbox event payload");
        }

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(serializedPayload)
                .status(OutboxStatus.PENDING)
                .build();

        outboxRepository.save(event);
    }
}
