package com.groupthree.Facilitybook.dto.building;

import com.groupthree.Facilitybook.entity.Building;

import java.time.Instant;

public record BuildingResponse(
        Long id,
        String name,
        String address,
        Instant createdAt,
        Instant updatedAt) {

    public static BuildingResponse from(Building building) {
        return new BuildingResponse(
                building.getId(),
                building.getName(),
                building.getAddress(),
                building.getCreatedAt(),
                building.getUpdatedAt());
    }
}
