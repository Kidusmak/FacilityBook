package com.groupthree.Facilitybook.dto.user;

import com.groupthree.Facilitybook.entity.User;

/**
 * Safe user projection embedded in other responses (never includes the password hash).
 */
public record UserSummary(
        Long id,
        String email,
        String fullName,
        String role,
        Long departmentId,
        String departmentName) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getDepartment() != null ? user.getDepartment().getId() : null,
                user.getDepartment() != null ? user.getDepartment().getName() : null);
    }
}
