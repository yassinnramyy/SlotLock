package com.slotlock.booking.controller;

import com.slotlock.booking.dto.request.BookingRequest;
import com.slotlock.booking.dto.response.BookingResponse;
import com.slotlock.booking.service.BookingService;
import com.slotlock.booking.service.BookingService.BookingResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<BookingResponse> book(@Valid @RequestBody BookingRequest request) {
        BookingResult result = bookingService.book(request);
        return ResponseEntity.status(result.isNew() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.booking());
    }

    @PostMapping("/optimistic")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<BookingResponse> bookOptimistic(@Valid @RequestBody BookingRequest request) {
        BookingResult result = bookingService.bookOptimistic(request);
        return ResponseEntity.status(result.isNew() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.booking());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<List<BookingResponse>> list() {
        return ResponseEntity.ok(bookingService.listForCurrentUser());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getById(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        bookingService.cancel(id);
        return ResponseEntity.ok(bookingService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
