package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Page<Building> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
            String name, String address, Pageable pageable);

    Optional<Building> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
