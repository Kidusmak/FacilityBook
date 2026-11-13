package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    @Override
    @EntityGraph(attributePaths = {"category", "category.approverRole", "building"})
    Page<Resource> findAll(Specification<Resource> spec, Pageable pageable);

    List<Resource> findByStatus(ResourceStatus status);

    long countByStatus(ResourceStatus status);

    long countByCategoryId(Long categoryId);

    long countByBuildingId(Long buildingId);

    boolean existsByNameIgnoreCaseAndBuildingId(String name, Long buildingId);

    boolean existsByNameIgnoreCaseAndBuildingIdAndIdNot(String name, Long buildingId, Long id);
}
