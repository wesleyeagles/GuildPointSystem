package com.guild.app.common.enums;

public enum Role {
    MEMBRO,
    MODERADOR,
    ADMINISTRADOR,
    LIDER;

    public boolean isAtLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
