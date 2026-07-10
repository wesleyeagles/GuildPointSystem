package com.guild.app.member.dto;

import com.guild.app.common.enums.MemberStatus;

public record ApprovalRequest(
        MemberStatus status
) {}
