package com.groupthree.Facilitybook.dto.resource;

import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import jakarta.validation.constraints.NotNull;

public record ResourceStatusRequest(

        @NotNull(message = "Status is required")
        ResourceStatus status) {
}
