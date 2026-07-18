package com.slotlock.booking.service;

import com.slotlock.booking.dto.request.ResourceRequest;
import com.slotlock.booking.dto.response.ResourceResponse;

import java.util.List;

public interface ResourceService {

    ResourceResponse create(ResourceRequest request);

    ResourceResponse getById(Long id);

    List<ResourceResponse> getAll(Long tenantId);

    ResourceResponse update(Long id, ResourceRequest request);

    void delete(Long id);
}
