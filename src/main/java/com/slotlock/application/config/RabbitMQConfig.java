package com.slotlock.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "slotlock.events";
    public static final String OUTBOX_RELAY_QUEUE = "outbox.relay.queue";
    public static final String WAITLIST_PROMOTION_QUEUE = "waitlist.promotion.queue";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE);
    }

    @Bean
    public Queue outboxRelayQueue() {
        return new Queue(OUTBOX_RELAY_QUEUE);
    }

    @Bean
    public Queue waitlistPromotionQueue() {
        return new Queue(WAITLIST_PROMOTION_QUEUE);
    }

    @Bean
    public Binding outboxRelayBinding(Queue outboxRelayQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(outboxRelayQueue).to(eventsExchange).with("outbox.#");
    }

    @Bean
    public Binding waitlistPromotionBinding(Queue waitlistPromotionQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(waitlistPromotionQueue).to(eventsExchange).with("slot.opened");
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
