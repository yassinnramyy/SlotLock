package com.slotlock.masterdata.service.impl;

import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.BusinessLogicViolationException;
import com.slotlock.application.repository.UserRepository;
import com.slotlock.masterdata.dto.request.TenantRequest;
import com.slotlock.masterdata.dto.response.TenantResponse;
import com.slotlock.masterdata.entity.Tenant;
import com.slotlock.masterdata.mapper.TenantMapper;
import com.slotlock.masterdata.repository.TenantRepository;
import com.slotlock.masterdata.service.TenantService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultTenantService implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final UserRepository userRepository;

    public DefaultTenantService(TenantRepository tenantRepository, TenantMapper tenantMapper, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
        this.userRepository = userRepository;
    }

    @Override
    public TenantResponse create(TenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.CONFLICT, ApiErrorCodeEnum.BUSINESS_RULE_VIOLATION, "A tenant with this slug already exists");
        }

        Tenant tenant = tenantMapper.toEntity(request);
        tenant = tenantRepository.save(tenant);
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public TenantResponse getById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Tenant not found"));
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public TenantResponse getBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Tenant not found"));
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public List<TenantResponse> getAll() {
        return tenantRepository.findAll().stream()
                .map(tenantMapper::toResponse)
                .toList();
    }

    @Override
    public TenantResponse update(Long id, TenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Tenant not found"));

        if (!tenant.getSlug().equals(request.slug()) && tenantRepository.existsBySlug(request.slug())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.CONFLICT, ApiErrorCodeEnum.BUSINESS_RULE_VIOLATION, "A tenant with this slug already exists");
        }

        tenant.setName(request.name());
        tenant.setSlug(request.slug());
        tenant = tenantRepository.save(tenant);
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public void delete(Long id) {
        if (!tenantRepository.existsById(id)) {
            throw new BusinessLogicViolationException(
                    HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Tenant not found");
        }
        if (userRepository.existsByTenantId(id)) {
            throw new BusinessLogicViolationException(
                    HttpStatus.CONFLICT, ApiErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "Tenant has users assigned and cannot be deleted");
        }
        tenantRepository.deleteById(id);
    }
}