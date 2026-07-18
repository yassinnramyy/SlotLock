package com.slotlock.booking.mapper;

import com.slotlock.booking.dto.response.WaitlistEntryResponse;
import com.slotlock.booking.entity.WaitlistEntry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WaitlistMapper {

    WaitlistEntryResponse toResponse(WaitlistEntry entry);
}
