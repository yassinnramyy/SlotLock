package com.slotlock.booking.entity;

import com.slotlock.application.entity.BaseEntity;
import com.slotlock.booking.enums.WaitlistStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry extends BaseEntity {

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "requested_start_at", nullable = false)
    private LocalDateTime requestedStartAt;

    @Column(name = "requested_end_at", nullable = false)
    private LocalDateTime requestedEndAt;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WaitlistStatus status;
}
