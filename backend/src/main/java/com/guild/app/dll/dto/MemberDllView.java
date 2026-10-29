package com.guild.app.dll.dto;

import java.time.Instant;

/**
 * Versao da DLL de cada membro aprovado (painel da administracao).
 *
 * @param dllVersion versao do ultimo envio (null = nunca sincronizou ou cliente sem versao)
 * @param outdated   sincroniza com uma versao anterior a mais recente publicada
 */
public record MemberDllView(
        Long memberId,
        String nickname,
        String dllVersion,
        Instant lastSyncAt,
        boolean outdated
) {}
