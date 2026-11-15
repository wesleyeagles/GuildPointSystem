package com.guild.app.damagepreset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.app.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Presets da calculadora de dano guardados no membro (antes ficavam só no navegador). O estado é o JSON da
 * página (arma, Keen, acessórios, buffs, relics...): o servidor só guarda, quem valida e limpa é o front.
 */
@Service
@RequiredArgsConstructor
public class DamagePresetService {

    public static final int NAME_MAX = 40;
    public static final int MAX_PRESETS = 50;
    /** Um estado da página tem ~1 KB; isso barra lixo grande. */
    static final int STATE_MAX_BYTES = 16 * 1024;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public record Preset(String name, JsonNode state, Instant updatedAt) {}

    public List<Preset> list(long memberId) {
        return jdbc.query("SELECT name, state::text, updated_at FROM damage_preset WHERE member_id=? ORDER BY created_at, id",
                (r, n) -> new Preset(r.getString(1), parse(r.getString(2)), r.getTimestamp(3).toInstant()), memberId);
    }

    @Transactional
    public List<Preset> save(long memberId, String name, JsonNode state) {
        String clean = cleanName(name);
        String json = json(state);
        int updated = jdbc.update("UPDATE damage_preset SET name=?, state=?::jsonb, updated_at=now() WHERE member_id=? AND lower(name)=lower(?)",
                clean, json, memberId, clean);
        if (updated == 0) {
            if (count(memberId) >= MAX_PRESETS)
                throw new AppException("Limite de " + MAX_PRESETS + " presets. Exclua algum para salvar outro.", HttpStatus.CONFLICT);
            jdbc.update("INSERT INTO damage_preset(member_id, name, state) VALUES(?,?,?::jsonb)", memberId, clean, json);
        }
        return list(memberId);
    }

    @Transactional
    public List<Preset> importAll(long memberId, List<Preset> presets) {
        for (Preset p : presets) {
            if (count(memberId) >= MAX_PRESETS) break;
            jdbc.update("INSERT INTO damage_preset(member_id, name, state) VALUES(?,?,?::jsonb) ON CONFLICT DO NOTHING",
                    memberId, cleanName(p.name()), json(p.state()));
        }
        return list(memberId);
    }

    @Transactional
    public List<Preset> delete(long memberId, String name) {
        jdbc.update("DELETE FROM damage_preset WHERE member_id=? AND lower(name)=lower(?)", memberId, name.strip());
        return list(memberId);
    }

    private int count(long memberId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM damage_preset WHERE member_id=?", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    private static String cleanName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty()) throw new AppException("Dê um nome ao preset.", HttpStatus.BAD_REQUEST);
        if (clean.length() > NAME_MAX) throw new AppException("Nome com no máximo " + NAME_MAX + " letras.", HttpStatus.BAD_REQUEST);
        return clean;
    }

    private String json(JsonNode state) {
        if (state == null || !state.isObject()) throw new AppException("Preset inválido.", HttpStatus.BAD_REQUEST);
        try {
            String json = mapper.writeValueAsString(state);
            if (json.length() > STATE_MAX_BYTES) throw new AppException("Preset grande demais.", HttpStatus.BAD_REQUEST);
            return json;
        } catch (JsonProcessingException e) {
            throw new AppException("Preset inválido.", HttpStatus.BAD_REQUEST);
        }
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }
}
