package com.groupthree.Facilitybook.dto.resource;

import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ResourceRequest(

        @NotBlank(message = "Resource name is required")
        @Size(min = 2, max = 150, message = "Resource name must be between 2 and 150 characters")
        String name,

        String description,

        @NotNull(message = "Category is required")
        Long categoryId,

        Long buildingId,

        @Size(max = 20, message = "Floor must be at most 20 characters")
        String floor,

        @Size(max = 150, message = "Location detail must be at most 150 characters")
        String locationDetail,

        @Positive(message = "Capacity must be greater than zero")
        Integer capacity,

        @Size(max = 500, message = "Features must be at most 500 characters")
        String features,

        ResourceStatus status) {
}
