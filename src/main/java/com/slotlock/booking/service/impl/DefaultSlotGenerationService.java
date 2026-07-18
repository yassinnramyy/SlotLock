package com.slotlock.booking.service.impl;

import com.slotlock.application.config.SecurityUtils;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.BusinessLogicViolationException;
import com.slotlock.booking.entity.AvailabilityWindow;
import com.slotlock.booking.entity.Resource;
import com.slotlock.booking.entity.Slot;
import com.slotlock.booking.enums.SlotStatus;
import com.slotlock.booking.dto.response.SlotResponse;
import com.slotlock.booking.mapper.SlotMapper;
import com.slotlock.booking.repository.AvailabilityWindowRepository;
import com.slotlock.booking.repository.ResourceRepository;
import com.slotlock.booking.repository.SlotRepository;
import com.slotlock.booking.service.SlotGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DefaultSlotGenerationService implements SlotGenerationService {

    private static final int MAX_RANGE_DAYS = 90;

    private final ResourceRepository resourceRepository;
    private final AvailabilityWindowRepository availabilityWindowRepository;
    private final SlotRepository slotRepository;
    private final SlotMapper slotMapper;

    public DefaultSlotGenerationService(ResourceRepository resourceRepository,
                                         AvailabilityWindowRepository availabilityWindowRepository,
                                         SlotRepository slotRepository,
                                         SlotMapper slotMapper) {
        this.resourceRepository = resourceRepository;
        this.availabilityWindowRepository = availabilityWindowRepository;
        this.slotRepository = slotRepository;
        this.slotMapper = slotMapper;
    }

    @Override
    public List<SlotResponse> generateAndGetSlots(Long resourceId, LocalDate from, LocalDate to) {
        // Same tenant-scoping split as DefaultResourceService.getById: ADMIN/STAFF stay locked to
        // their own tenant, customers (tenant-less) can view slots for any tenant's resource.
        Resource resource = isTenantScopedCaller()
                ? resourceRepository.findByIdAndTenantId(resourceId, SecurityUtils.getCurrentTenantId())
                        .orElseThrow(() -> new BusinessLogicViolationException(
                                HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found"))
                : resourceRepository.findById(resourceId)
                        .orElseThrow(() -> new BusinessLogicViolationException(
                                HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found"));

        validateRange(from, to);

        List<AvailabilityWindow> windows = availabilityWindowRepository.findByResourceId(resourceId);

        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.atTime(LocalTime.MAX);

        List<Slot> existingSlots = slotRepository.findByResourceIdAndStartAtBetween(resourceId, rangeStart, rangeEnd);
        Set<LocalDateTime> existingStarts = existingSlots.stream()
                .map(Slot::getStartAt)
                .collect(Collectors.toSet());

        List<Slot> newSlots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int ourDayOfWeek = date.getDayOfWeek().getValue() % 7;

            for (AvailabilityWindow window : windows) {
                if (!window.getDayOfWeek().equals(ourDayOfWeek)) {
                    continue;
                }

                LocalTime candidateStart = window.getStartTime();
                while (true) {
                    LocalTime candidateEnd = candidateStart.plusMinutes(resource.getSlotDurationMinutes());
                    if (candidateEnd.isAfter(window.getEndTime())) {
                        break;
                    }

                    LocalDateTime candidateStartDateTime = LocalDateTime.of(date, candidateStart);
                    if (!candidateStartDateTime.isBefore(now) && !existingStarts.contains(candidateStartDateTime)) {
                        newSlots.add(Slot.builder()
                                .resourceId(resourceId)
                                .startAt(candidateStartDateTime)
                                .endAt(LocalDateTime.of(date, candidateEnd))
                                .status(SlotStatus.OPEN)
                                .build());
                        existingStarts.add(candidateStartDateTime);
                    }

                    candidateStart = candidateEnd;
                }
            }
        }

        List<Slot> savedSlots = slotRepository.saveAll(newSlots);

        List<Slot> allSlots = new ArrayList<>(existingSlots);
        allSlots.addAll(savedSlots);
        allSlots.sort(Comparator.comparing(Slot::getStartAt));

        return allSlots.stream()
                .map(slotMapper::toResponse)
                .toList();
    }

    private boolean isTenantScopedCaller() {
        String role = SecurityUtils.getCurrentUserRole();
        return "ADMIN".equals(role) || "STAFF".equals(role);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessLogicViolationException(
                    HttpStatus.BAD_REQUEST, ApiErrorCodeEnum.VALIDATION_ERROR,
                    "Date range must be between 1 and 90 days");
        }
    }
}
