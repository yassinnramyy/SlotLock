package com.slotlock.outbox.service;

public interface OutboxService {

    void recordEvent(String aggregateType, Long aggregateId, String eventType, Object payload);
}
