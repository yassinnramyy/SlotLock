package com.slotlock.masterdata.service;

import com.slotlock.masterdata.dto.request.TenantRequest;
import com.slotlock.masterdata.dto.response.TenantResponse;
import com.slotlock.masterdata.enums.TenantCategoryEnum;

import java.util.List;

public interface TenantService {

    TenantResponse create(TenantRequest request);

    TenantResponse getById(Long id);

    TenantResponse getBySlug(String slug);

    List<TenantResponse> getAll(TenantCategoryEnum category);

    TenantResponse update(Long id, TenantRequest request);

    void delete(Long id);
}