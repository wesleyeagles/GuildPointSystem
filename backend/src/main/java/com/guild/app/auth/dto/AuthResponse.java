package com.guild.app.auth.dto;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;

public record AuthResponse(
        String token,
        Long memberId,
        String nickname,
        Role role,
        MemberStatus status,
        int level,
        boolean profileComplete
) {}
