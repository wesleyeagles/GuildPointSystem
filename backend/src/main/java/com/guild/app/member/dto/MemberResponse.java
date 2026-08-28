package com.guild.app.member.dto;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        String avatarUrl,
        Long raceId,
        String raceName,
        Long classId,
        String className,
        String classImageUrl,
        Role role,
        MemberStatus status,
        Long points,
        Long availablePoints,
        boolean profileComplete
) {}
