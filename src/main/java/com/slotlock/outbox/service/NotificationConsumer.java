package com.slotlock.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.slotlock.application.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.OUTBOX_RELAY_QUEUE)
    public void handle(JsonNode payload) {
        log.info("Notification: {}", payload);
    }
}
