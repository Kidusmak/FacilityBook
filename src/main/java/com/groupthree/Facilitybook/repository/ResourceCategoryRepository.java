package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.ResourceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResourceCategoryRepository extends JpaRepository<ResourceCategory, Long> {

    Page<ResourceCategory> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<ResourceCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
