package com.groupthree.Facilitybook.dto.resource;

import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;

/**
 * Compact resource projection embedded in reservation responses.
 */
public record ResourceSummary(
        Long id,
        String name,
        String categoryName,
        String buildingName,
        String floor,
        String locationDetail,
        Integer capacity,
        ResourceStatus status) {

    public static ResourceSummary from(Resource resource) {
        return new ResourceSummary(
                resource.getId(),
                resource.getName(),
                resource.getCategory().getName(),
                resource.getBuilding() != null ? resource.getBuilding().getName() : null,
                resource.getFloor(),
                resource.getLocationDetail(),
                resource.getCapacity(),
                resource.getStatus());
    }
}
