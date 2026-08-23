package com.guild.app.item.util;

import java.math.BigDecimal;

public final class DefFacingUtil {

    private DefFacingUtil() {}

    /**
     * Converts server DefFacing to in-game DSR integer (client display).
     * DSR = (100 / val)^(1/1.3) + 0.5
     */
    public static Integer toClientDsr(BigDecimal defFacing) {
        if (defFacing == null || defFacing.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        double val = defFacing.doubleValue();
        double dsr = Math.pow(100.0 / val, 1.0 / 1.3) + 0.5;
        return (int) Math.floor(dsr);
    }
}
