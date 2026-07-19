package com.slotlock.outbox.repository;

import com.slotlock.outbox.entity.OutboxEvent;
import com.slotlock.outbox.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
