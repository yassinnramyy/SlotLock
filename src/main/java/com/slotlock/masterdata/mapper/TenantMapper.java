package com.slotlock.masterdata.mapper;

import com.slotlock.masterdata.dto.request.TenantRequest;
import com.slotlock.masterdata.dto.response.TenantResponse;
import com.slotlock.masterdata.entity.Tenant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    Tenant toEntity(TenantRequest request);

    TenantResponse toResponse(Tenant tenant);
}