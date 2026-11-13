package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.auth.AuthResponse;
import com.groupthree.Facilitybook.dto.auth.LoginRequest;
import com.groupthree.Facilitybook.dto.user.UserResponse;
import com.groupthree.Facilitybook.security.JwtService;
import com.groupthree.Facilitybook.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(principal));
    }
}
