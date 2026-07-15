package com.slotlock.booking.mapper;

import com.slotlock.booking.dto.request.AvailabilityWindowRequest;
import com.slotlock.booking.dto.response.AvailabilityWindowResponse;
import com.slotlock.booking.entity.AvailabilityWindow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AvailabilityWindowMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "resourceId", ignore = true)
    AvailabilityWindow toEntity(AvailabilityWindowRequest request);

    AvailabilityWindowResponse toResponse(AvailabilityWindow window);
}
