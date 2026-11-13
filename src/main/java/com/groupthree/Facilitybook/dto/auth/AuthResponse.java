package com.groupthree.Facilitybook.dto.auth;

import com.groupthree.Facilitybook.dto.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user) {
}
