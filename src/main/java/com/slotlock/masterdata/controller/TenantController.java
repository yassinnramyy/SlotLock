package com.slotlock.masterdata.controller;

import com.slotlock.masterdata.dto.request.TenantRequest;
import com.slotlock.masterdata.dto.response.TenantResponse;
import com.slotlock.masterdata.enums.TenantCategoryEnum;
import com.slotlock.masterdata.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<List<TenantResponse>> getAll(
            @RequestParam(required = false) TenantCategoryEnum category) {
        return ResponseEntity.ok(tenantService.getAll(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<TenantResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(tenantService.getBySlug(slug));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> update(@PathVariable Long id, @Valid @RequestBody TenantRequest request) {
        return ResponseEntity.ok(tenantService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
