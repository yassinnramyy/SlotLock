package com.slotlock.booking.service;

import com.slotlock.booking.dto.request.WaitlistRequest;
import com.slotlock.booking.dto.response.WaitlistEntryResponse;

import java.util.List;

public interface WaitlistService {

    WaitlistEntryResponse join(WaitlistRequest request);

    List<WaitlistEntryResponse> listForCurrentUser();
}
