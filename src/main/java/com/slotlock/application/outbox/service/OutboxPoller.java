package com.slotlock.application.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotlock.application.config.RabbitMQConfig;
import com.slotlock.application.outbox.entity.OutboxEvent;
import com.slotlock.application.outbox.enums.OutboxStatus;
import com.slotlock.application.outbox.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                // rabbitTemplate.convertAndSend would otherwise re-serialize this raw JSON String
                // as a JSON string literal via Jackson2JsonMessageConverter (double-encoding it).
                // Parsing it back to a JsonNode first publishes the actual object structure.
                JsonNode node = objectMapper.readTree(event.getPayload());

                // eventType IS the routing key: "outbox.*" reaches the notification consumer
                // (bound to "outbox.#"), while "slot.opened" (no prefix) reaches the waitlist
                // promotion listener (bound to that exact key).
                rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, event.getEventType(), node);

                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                event.setStatus(OutboxStatus.FAILED);
                outboxRepository.save(event);
                log.error("Failed to relay outbox event {} (eventType={})", event.getId(), event.getEventType(), e);
            }
        }
    }
}
