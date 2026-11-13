package com.groupthree.Facilitybook.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single access point for the authenticated identity. Services derive ownership and
 * role checks from here rather than trusting ids supplied by clients.
 */
@Component
public class CurrentUser {

    public UserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("Authentication required");
    }

    public Long id() {
        return require().getId();
    }

    public String role() {
        return require().getRole();
    }

    public boolean hasRole(String... roles) {
        String current = role();
        for (String role : roles) {
            if (role.equals(current)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
