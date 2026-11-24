package com.guild.app.dll.dto;

import java.time.Instant;

/** Recent, unverified client uploads. Never broadcast on public topics. */
public record DllConnectionView(Long memberId, String nickname, String character, String ip,
                                Instant lastUploadAt, String dllVersion, long sameIpCharacters) {}
