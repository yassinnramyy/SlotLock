package com.slotlock.booking.service.impl;

import com.slotlock.application.config.SecurityUtils;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.BusinessLogicViolationException;
import com.slotlock.booking.dto.request.ResourceRequest;
import com.slotlock.booking.dto.response.ResourceResponse;
import com.slotlock.booking.entity.AvailabilityWindow;
import com.slotlock.booking.entity.Resource;
import com.slotlock.booking.mapper.AvailabilityWindowMapper;
import com.slotlock.booking.mapper.ResourceMapper;
import com.slotlock.booking.enums.SlotStatus;
import com.slotlock.booking.repository.AvailabilityWindowRepository;
import com.slotlock.booking.repository.ResourceRepository;
import com.slotlock.booking.repository.SlotRepository;
import com.slotlock.booking.service.ResourceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultResourceService implements ResourceService {

    private static final int DEFAULT_SLOT_DURATION_MINUTES = 30;

    private final ResourceRepository resourceRepository;
    private final AvailabilityWindowRepository availabilityWindowRepository;
    private final SlotRepository slotRepository;
    private final ResourceMapper resourceMapper;
    private final AvailabilityWindowMapper availabilityWindowMapper;

    public DefaultResourceService(ResourceRepository resourceRepository,
                                   AvailabilityWindowRepository availabilityWindowRepository,
                                   SlotRepository slotRepository,
                                   ResourceMapper resourceMapper,
                                   AvailabilityWindowMapper availabilityWindowMapper) {
        this.resourceRepository = resourceRepository;
        this.availabilityWindowRepository = availabilityWindowRepository;
        this.slotRepository = slotRepository;
        this.resourceMapper = resourceMapper;
        this.availabilityWindowMapper = availabilityWindowMapper;
    }

    @Override
    public ResourceResponse create(ResourceRequest request) {
        Resource resource = Resource.builder()
                .tenantId(SecurityUtils.getCurrentTenantId())
                .name(request.name())
                .description(request.description())
                .slotDurationMinutes(request.slotDurationMinutes() != null
                        ? request.slotDurationMinutes() : DEFAULT_SLOT_DURATION_MINUTES)
                .build();

        Resource savedResource = resourceRepository.save(resource);

        List<AvailabilityWindow> windows = request.availabilityWindows().stream()
                .map(availabilityWindowMapper::toEntity)
                .peek(window -> window.setResourceId(savedResource.getId()))
                .toList();

        List<AvailabilityWindow> savedWindows = availabilityWindowRepository.saveAll(windows);

        return resourceMapper.toResponse(savedResource, savedWindows);
    }

    @Override
    public ResourceResponse getById(Long id) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found"));

        List<AvailabilityWindow> windows = availabilityWindowRepository.findByResourceId(resource.getId());
        return resourceMapper.toResponse(resource, windows);
    }

    @Override
    public List<ResourceResponse> getAllForCurrentTenant() {
        Long tenantId = SecurityUtils.getCurrentTenantId();

        return resourceRepository.findByTenantId(tenantId).stream()
                .map(resource -> resourceMapper.toResponse(
                        resource, availabilityWindowRepository.findByResourceId(resource.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ResourceResponse update(Long id, ResourceRequest request) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found"));

        resource.setName(request.name());
        resource.setDescription(request.description());
        resource.setSlotDurationMinutes(request.slotDurationMinutes() != null
                ? request.slotDurationMinutes() : DEFAULT_SLOT_DURATION_MINUTES);
        Resource savedResource = resourceRepository.save(resource);

        availabilityWindowRepository.deleteByResourceId(savedResource.getId());

        List<AvailabilityWindow> windows = request.availabilityWindows().stream()
                .map(availabilityWindowMapper::toEntity)
                .peek(window -> window.setResourceId(savedResource.getId()))
                .toList();

        List<AvailabilityWindow> savedWindows = availabilityWindowRepository.saveAll(windows);

        return resourceMapper.toResponse(savedResource, savedWindows);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Resource not found"));

        if (slotRepository.existsByResourceIdAndStatus(resource.getId(), SlotStatus.BOOKED)) {
            throw new BusinessLogicViolationException(
                    HttpStatus.CONFLICT, ApiErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "Resource has active bookings and cannot be deleted");
        }

        slotRepository.deleteByResourceId(resource.getId());
        availabilityWindowRepository.deleteByResourceId(resource.getId());
        resourceRepository.delete(resource);
    }
}
