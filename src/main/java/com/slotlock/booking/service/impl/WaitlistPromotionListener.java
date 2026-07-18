package com.slotlock.booking.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.slotlock.application.config.RabbitMQConfig;
import com.slotlock.application.config.TenantContext;
import com.slotlock.application.exception.SlotConflictException;
import com.slotlock.booking.dto.request.BookingRequest;
import com.slotlock.booking.entity.Resource;
import com.slotlock.booking.entity.WaitlistEntry;
import com.slotlock.booking.enums.WaitlistStatus;
import com.slotlock.booking.repository.ResourceRepository;
import com.slotlock.booking.repository.WaitlistRepository;
import com.slotlock.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WaitlistPromotionListener {

    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionListener.class);

    private final WaitlistRepository waitlistRepository;
    private final ResourceRepository resourceRepository;
    private final BookingService bookingService;

    public WaitlistPromotionListener(WaitlistRepository waitlistRepository,
                                      ResourceRepository resourceRepository,
                                      BookingService bookingService) {
        this.waitlistRepository = waitlistRepository;
        this.resourceRepository = resourceRepository;
        this.bookingService = bookingService;
    }

    @RabbitListener(queues = RabbitMQConfig.WAITLIST_PROMOTION_QUEUE)
    public void handle(JsonNode payload) {
        Long resourceId = payload.get("resourceId").asLong();
        Long slotId = payload.get("slotId").asLong();

        Optional<WaitlistEntry> maybeEntry = waitlistRepository
                .findFirstByResourceIdAndStatusOrderByCreatedAtAsc(resourceId, WaitlistStatus.WAITING);
        if (maybeEntry.isEmpty()) {
            return;
        }
        WaitlistEntry entry = maybeEntry.get();

        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            log.warn("Waitlist promotion skipped: resource {} no longer exists", resourceId);
            return;
        }

        // Same ThreadLocal issue as BookingConcurrencyTest (see CLAUDE.md): this handler runs on
        // a RabbitMQ listener thread that never went through JwtAuthenticationFilter/TenantFilter,
        // so SecurityUtils.getCurrentUserId()/getCurrentTenantId() would throw IllegalStateException
        // with nothing set. Seed both manually before calling into booking logic that reads them,
        // and clear in finally — listener container threads are pooled and reused for the next
        // message, so leaving this customer's identity set would leak into whatever runs next.
        // Note: book() itself won't actually need the TenantContext value here since the promoted
        // customer is CUSTOMER-role and isTenantScopedCaller() skips the tenant check for them
        // (see DefaultBookingService.book()) — set it anyway for consistency and any future
        // tenant-aware logic that reads it.
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(entry.getCustomerId(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            TenantContext.set(resource.getTenantId());

            String idempotencyKey = "waitlist-promotion-" + entry.getId();
            bookingService.book(new BookingRequest(slotId, idempotencyKey));

            entry.setStatus(WaitlistStatus.PROMOTED);
            waitlistRepository.save(entry);
        } catch (SlotConflictException e) {
            log.info("Waitlist promotion skipped for entry {}: slot {} is no longer available",
                    entry.getId(), slotId);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
