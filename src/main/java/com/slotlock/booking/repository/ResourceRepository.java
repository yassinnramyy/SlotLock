package com.slotlock.booking.repository;

import com.slotlock.booking.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByTenantId(Long tenantId);

    Optional<Resource> findByIdAndTenantId(Long id, Long tenantId);
}
