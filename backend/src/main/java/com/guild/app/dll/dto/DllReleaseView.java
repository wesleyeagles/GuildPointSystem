package com.guild.app.dll.dto;

import java.time.Instant;

public record DllReleaseView(
        Long id,
        String version,
        String notes,
        String fileName,
        Integer fileSize,
        String publishedBy,
        Instant publishedAt,
        /** Publicada com o version.dll assinado: as DLLs instaladas se atualizam sozinhas. */
        boolean autoUpdate
) {}
