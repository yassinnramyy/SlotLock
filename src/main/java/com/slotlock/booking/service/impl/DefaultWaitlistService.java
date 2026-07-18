package com.slotlock.booking.service.impl;

import com.slotlock.application.config.SecurityUtils;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.BusinessLogicViolationException;
import com.slotlock.booking.dto.request.WaitlistRequest;
import com.slotlock.booking.dto.response.WaitlistEntryResponse;
import com.slotlock.booking.entity.Resource;
import com.slotlock.booking.entity.WaitlistEntry;
import com.slotlock.booking.enums.WaitlistStatus;
import com.slotlock.booking.mapper.WaitlistMapper;
import com.slotlock.booking.repository.ResourceRepository;
import com.slotlock.booking.repository.WaitlistRepository;
import com.slotlock.booking.service.WaitlistService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultWaitlistService implements WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final ResourceRepository resourceRepository;
    private final WaitlistMapper waitlistMapper;

    public DefaultWaitlistService(WaitlistRepository waitlistRepository,
                                   ResourceRepository resourceRepository,
                                   WaitlistMapper waitlistMapper) {
        this.waitlistRepository = waitlistRepository;
        this.resourceRepository = resourceRepository;
        this.waitlistMapper = waitlistMapper;
    }

    @Override
    public WaitlistEntryResponse join(WaitlistRequest request) {
        // Same tenant-scoped-vs-unscoped pattern as DefaultBookingService.book(): plain findById
        // (not findByIdAndTenantId), then only enforce a tenant match for ADMIN/STAFF. A
        // tenant-less CUSTOMER must be able to join the waitlist for any tenant's resource,
        // exactly like they can book one.
        Resource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found"));

        if (isTenantScopedCaller() && !resource.getTenantId().equals(SecurityUtils.getCurrentTenantId())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found");
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .resourceId(resource.getId())
                .requestedStartAt(request.requestedStartAt())
                .requestedEndAt(request.requestedEndAt())
                .customerId(SecurityUtils.getCurrentUserId())
                .status(WaitlistStatus.WAITING)
                .build();

        WaitlistEntry saved = waitlistRepository.save(entry);
        return waitlistMapper.toResponse(saved);
    }

    @Override
    public List<WaitlistEntryResponse> listForCurrentUser() {
        return waitlistRepository.findByCustomerId(SecurityUtils.getCurrentUserId()).stream()
                .map(waitlistMapper::toResponse)
                .toList();
    }

    private boolean isTenantScopedCaller() {
        String role = SecurityUtils.getCurrentUserRole();
        return "ADMIN".equals(role) || "STAFF".equals(role);
    }
}
