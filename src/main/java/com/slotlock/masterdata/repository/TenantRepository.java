package com.slotlock.masterdata.repository;

import com.slotlock.masterdata.entity.Tenant;
import com.slotlock.masterdata.enums.TenantCategoryEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Tenant> findByCategory(TenantCategoryEnum category);
}