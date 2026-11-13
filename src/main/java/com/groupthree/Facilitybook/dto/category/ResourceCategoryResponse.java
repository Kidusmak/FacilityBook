package com.groupthree.Facilitybook.dto.category;

import com.groupthree.Facilitybook.entity.ResourceCategory;

import java.time.Instant;

public record ResourceCategoryResponse(
        Long id,
        String name,
        String description,
        boolean requiresApproval,
        Short approverRoleId,
        String approverRoleName,
        Instant createdAt,
        Instant updatedAt) {

    public static ResourceCategoryResponse from(ResourceCategory category) {
        return new ResourceCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isRequiresApproval(),
                category.getApproverRole() != null ? category.getApproverRole().getId() : null,
                category.getApproverRole() != null ? category.getApproverRole().getName() : null,
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
