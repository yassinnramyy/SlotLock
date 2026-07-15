package com.slotlock.booking.mapper;

import com.slotlock.booking.dto.response.SlotResponse;
import com.slotlock.booking.entity.Slot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SlotMapper {

    @Mapping(target = "status", source = "status")
    SlotResponse toResponse(Slot slot);
}
