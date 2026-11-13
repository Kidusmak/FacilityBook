package com.groupthree.Facilitybook.dto.building;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuildingRequest(

        @NotBlank(message = "Building name is required")
        @Size(max = 100, message = "Building name must be at most 100 characters")
        String name,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address) {
}
