package com.slotlock.booking.controller;

import com.slotlock.booking.dto.request.WaitlistRequest;
import com.slotlock.booking.dto.response.WaitlistEntryResponse;
import com.slotlock.booking.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<WaitlistEntryResponse> join(@Valid @RequestBody WaitlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.join(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<List<WaitlistEntryResponse>> list() {
        return ResponseEntity.ok(waitlistService.listForCurrentUser());
    }
}
