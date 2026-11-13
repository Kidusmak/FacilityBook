package com.groupthree.Facilitybook.dto.user;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
        String fullName,

        Short roleId,

        Long departmentId,

        Boolean active,

        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password) {
}
