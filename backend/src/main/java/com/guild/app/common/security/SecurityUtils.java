package com.guild.app.common.security;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static MemberPrincipal currentMember() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MemberPrincipal principal)) {
            throw new AppException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    public static void requireRole(Role minimum) {
        if (!currentMember().getRole().isAtLeast(minimum)) {
            throw new AppException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
