package com.slotlock.booking.mapper;

import com.slotlock.booking.dto.response.BookingResponse;
import com.slotlock.booking.entity.Booking;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingResponse toResponse(Booking booking);
}
