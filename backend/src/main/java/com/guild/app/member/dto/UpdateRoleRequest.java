package com.guild.app.member.dto;

import com.guild.app.common.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull Role role
) {}
