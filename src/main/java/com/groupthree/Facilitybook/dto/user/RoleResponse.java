package com.groupthree.Facilitybook.dto.user;

import com.groupthree.Facilitybook.entity.Role;

public record RoleResponse(Short id, String name) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
