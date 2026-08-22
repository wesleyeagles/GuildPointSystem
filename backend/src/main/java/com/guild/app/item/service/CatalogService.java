package com.guild.app.item.service;

import com.guild.app.common.enums.EffectDisplayType;
import com.guild.app.common.exception.AppException;
import com.guild.app.item.dto.*;
import com.guild.app.item.entity.EffectDefinition;
import com.guild.app.item.entity.GameAccessory;
import com.guild.app.item.entity.ItemSet;
import com.guild.app.item.repository.EffectDefinitionRepository;
import com.guild.app.item.repository.GameAccessoryRepository;
import com.guild.app.item.repository.ItemSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final GameAccessoryRepository gameAccessoryRepository;
    private final EffectDefinitionRepository effectDefinitionRepository;
    private final ItemSetRepository itemSetRepository;

    public List<GameAccessoryResponse> listAccessories(String subtype, Integer grade, String civilMask, String search) {
        var effectMap = effectMap();
        return gameAccessoryRepository.findFiltered(subtype, grade, civilMask, blankToNull(search)).stream()
                .map(a -> toAccessoryResponse(a, effectMap))
                .toList();
    }

    public GameAccessoryResponse getAccessory(String gameCode) {
        var accessory = gameAccessoryRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new AppException("Acessório não encontrado no catálogo.", HttpStatus.NOT_FOUND));
        return toAccessoryResponse(accessory, effectMap());
    }

    public List<EffectDefinitionResponse> listEffects() {
        return effectDefinitionRepository.findAll().stream()
                .map(e -> new EffectDefinitionResponse(e.getCode(), e.getName(), e.getDisplayType()))
                .toList();
    }

    public List<ItemSetResponse> listSetsForGameCode(String gameCode) {
        var effectMap = effectMap();
        return itemSetRepository.findByGameCode(gameCode).stream()
                .map(s -> toSetResponse(s, effectMap))
                .toList();
    }

    private Map<Integer, EffectDefinition> effectMap() {
        return effectDefinitionRepository.findAll().stream()
                .collect(Collectors.toMap(EffectDefinition::getCode, Function.identity()));
    }

    private GameAccessoryResponse toAccessoryResponse(GameAccessory a, Map<Integer, EffectDefinition> effectMap) {
        var effects = new ArrayList<GameAccessoryEffectResponse>();
        addAccessoryEffect(effects, effectMap, a.getEffCode1(), a.getEffUnit1());
        addAccessoryEffect(effects, effectMap, a.getEffCode2(), a.getEffUnit2());
        addAccessoryEffect(effects, effectMap, a.getEffCode3(), a.getEffUnit3());
        addAccessoryEffect(effects, effectMap, a.getEffCode4(), a.getEffUnit4());

        return new GameAccessoryResponse(
                a.getId(), a.getGameCode(), a.getName(), a.getSubtype(),
                a.getIconId(), a.getSpriteSheet(), a.getGrade(), a.getCivilMask(),
                a.getLevelRequired(), a.getFire(), a.getWater(), a.getSoil(), a.getWind(),
                effects);
    }

    private void addAccessoryEffect(
            List<GameAccessoryEffectResponse> effects,
            Map<Integer, EffectDefinition> effectMap,
            Integer code,
            BigDecimal rawValue) {
        if (code == null || code == 0) return;
        var def = effectMap.get(code);
        var displayType = def != null ? def.getDisplayType() : EffectDisplayType.FLAT;
        var name = def != null ? def.getName() : "Effect " + code;
        effects.add(new GameAccessoryEffectResponse(
                code, name, displayType, rawValue, formatEffectValue(displayType, rawValue)));
    }

    private ItemSetResponse toSetResponse(ItemSet s, Map<Integer, EffectDefinition> effectMap) {
        var effects = new ArrayList<ItemSetEffectResponse>();
        addSetEffect(effects, effectMap, s.getEff1Code(), s.getEff1Unit());
        addSetEffect(effects, effectMap, s.getEff2Code(), s.getEff2Unit());
        addSetEffect(effects, effectMap, s.getEff3Code(), s.getEff3Unit());
        addSetEffect(effects, effectMap, s.getEff4Code(), s.getEff4Unit());
        addSetEffect(effects, effectMap, s.getEff5Code(), s.getEff5Unit());
        addSetEffect(effects, effectMap, s.getEff6Code(), s.getEff6Unit());
        addSetEffect(effects, effectMap, s.getEff7Code(), s.getEff7Unit());
        addSetEffect(effects, effectMap, s.getEff8Code(), s.getEff8Unit());

        return new ItemSetResponse(
                s.getId(), s.getSetCode(), s.getCivilMask(),
                s.getHead(), s.getUpper(), s.getLower(), s.getShoes(), s.getGauntlet(),
                s.getWeapon(), s.getShield(), s.getAmul1(), s.getAmul2(),
                s.getRing1(), s.getRing2(), s.getCloack(), effects);
    }

    private void addSetEffect(
            List<ItemSetEffectResponse> effects,
            Map<Integer, EffectDefinition> effectMap,
            Integer code,
            BigDecimal rawValue) {
        if (code == null || code == 0) return;
        var def = effectMap.get(code);
        var displayType = def != null ? def.getDisplayType() : EffectDisplayType.FLAT;
        var name = def != null ? def.getName() : "Effect " + code;
        effects.add(new ItemSetEffectResponse(
                code, name, displayType, rawValue, formatEffectValue(displayType, rawValue)));
    }

    static String formatEffectValue(EffectDisplayType displayType, BigDecimal rawValue) {
        if (displayType == EffectDisplayType.BOOLEAN) {
            return "true";
        }
        if (rawValue == null) return "";
        switch (displayType) {
            case PERCENT_100:
                var pct = rawValue.multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                return pct.toPlainString() + "%";
            case FLAT:
                return rawValue.setScale(0, RoundingMode.HALF_UP).toPlainString();
            default:
                return rawValue.toPlainString();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
