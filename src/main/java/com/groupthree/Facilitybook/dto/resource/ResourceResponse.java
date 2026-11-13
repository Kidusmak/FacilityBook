package com.groupthree.Facilitybook.dto.resource;

import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;

import java.time.Instant;
import java.util.List;

public record ResourceResponse(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        boolean requiresApproval,
        String approverRole,
        Long buildingId,
        String buildingName,
        String floor,
        String locationDetail,
        Integer capacity,
        String features,
        ResourceStatus status,
        List<OperatingHourResponse> operatingHours,
        Instant createdAt,
        Instant updatedAt) {

    public static ResourceResponse from(Resource resource) {
        return from(resource, List.of());
    }

    public static ResourceResponse from(Resource resource, List<OperatingHourResponse> operatingHours) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getDescription(),
                resource.getCategory().getId(),
                resource.getCategory().getName(),
                resource.getCategory().isRequiresApproval(),
                resource.getCategory().getApproverRole() != null
                        ? resource.getCategory().getApproverRole().getName()
                        : null,
                resource.getBuilding() != null ? resource.getBuilding().getId() : null,
                resource.getBuilding() != null ? resource.getBuilding().getName() : null,
                resource.getFloor(),
                resource.getLocationDetail(),
                resource.getCapacity(),
                resource.getFeatures(),
                resource.getStatus(),
                operatingHours,
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
