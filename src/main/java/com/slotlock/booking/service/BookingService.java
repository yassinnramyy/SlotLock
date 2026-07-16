package com.slotlock.booking.service;

import com.slotlock.booking.dto.request.BookingRequest;
import com.slotlock.booking.dto.response.BookingResponse;

import java.util.List;

public interface BookingService {

    BookingResult book(BookingRequest request);

    BookingResult bookOptimistic(BookingRequest request);

    void cancel(Long bookingId);

    BookingResponse getById(Long id);

    List<BookingResponse> listForCurrentUser();

    void delete(Long bookingId);

    record BookingResult(BookingResponse booking, boolean isNew) {
    }
}
