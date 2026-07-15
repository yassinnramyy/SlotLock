package com.slotlock.booking.service;

import com.slotlock.booking.dto.response.SlotResponse;

import java.time.LocalDate;
import java.util.List;

public interface SlotGenerationService {

    List<SlotResponse> generateAndGetSlots(Long resourceId, LocalDate from, LocalDate to);
}
