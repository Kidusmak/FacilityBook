package com.groupthree.Facilitybook.dto.user;

import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.security.UserPrincipal;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        Long departmentId,
        String departmentName,
        boolean active) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getDepartment() != null ? user.getDepartment().getId() : null,
                user.getDepartment() != null ? user.getDepartment().getName() : null,
                user.isActive());
    }

    public static UserResponse from(UserPrincipal principal) {
        return new UserResponse(
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole(),
                principal.getDepartmentId(),
                principal.getDepartmentName(),
                principal.isEnabled());
    }
}
