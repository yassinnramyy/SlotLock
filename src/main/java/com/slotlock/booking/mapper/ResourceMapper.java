package com.slotlock.booking.mapper;

import com.slotlock.booking.dto.response.ResourceResponse;
import com.slotlock.booking.entity.AvailabilityWindow;
import com.slotlock.booking.entity.Resource;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = AvailabilityWindowMapper.class)
public interface ResourceMapper {

    @Mapping(target = "availabilityWindows", source = "windows")
    ResourceResponse toResponse(Resource resource, List<AvailabilityWindow> windows);
}
