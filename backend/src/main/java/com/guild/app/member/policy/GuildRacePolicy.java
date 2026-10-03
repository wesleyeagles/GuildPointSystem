package com.guild.app.member.policy;

import com.guild.app.common.exception.AppException;
import com.guild.app.member.entity.GameRace;
import com.guild.app.member.entity.Member;
import org.springframework.http.HttpStatus;

public final class GuildRacePolicy {

    public static final String ALLOWED_RACE_NAME = "Cora";

    private GuildRacePolicy() {}

    public static void requireCoraRaceForRegistration(GameRace race) {
        if (!ALLOWED_RACE_NAME.equalsIgnoreCase(race.getName())) {
            throw new AppException(
                    "A guilda aceita apenas personagens da raça Cora.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public static void requireRaceUnchanged(Member member, Long requestedRaceId) {
        if (member.getRace() == null) {
            return;
        }
        if (!member.getRace().getId().equals(requestedRaceId)) {
            throw new AppException("Não é permitido alterar a raça.", HttpStatus.BAD_REQUEST);
        }
    }
}
