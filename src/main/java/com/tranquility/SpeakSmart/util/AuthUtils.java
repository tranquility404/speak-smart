package com.tranquility.SpeakSmart.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Collectors;

public class AuthUtils {
    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null)
            return authentication.getName();
        return null;
    }

    public static String getUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated())
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(authority -> authority.substring(5))
                    .collect(Collectors.joining(","));
        return null;
    }
}
