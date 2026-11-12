package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    List<Resource> findByStatus(ResourceStatus status);

    boolean existsByNameIgnoreCaseAndBuildingId(String name, Long buildingId);
}
