package com.guild.app.item.service;

import com.guild.app.common.enums.EffectDisplayType;
import com.guild.app.common.exception.AppException;
import com.guild.app.item.dto.*;
import com.guild.app.item.entity.EffectDefinition;
import com.guild.app.item.entity.GameAccessory;
import com.guild.app.item.entity.GameArmor;
import com.guild.app.item.entity.ItemSet;
import com.guild.app.item.repository.EffectDefinitionRepository;
import com.guild.app.item.repository.GameAccessoryRepository;
import com.guild.app.item.repository.GameArmorRepository;
import com.guild.app.item.repository.ItemSetRepository;
import com.guild.app.item.util.DefFacingUtil;
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
    private final GameArmorRepository gameArmorRepository;
    private final EffectDefinitionRepository effectDefinitionRepository;
    private final ItemSetRepository itemSetRepository;

    public List<GameAccessoryResponse> listAccessories(String subtype, Integer grade, String civilMask, String search) {
        var effectMap = effectMap();
        var normalizedSearch = blankToNull(search);
        var hasSearch = normalizedSearch != null;
        var searchPattern = hasSearch ? "%" + normalizedSearch.toLowerCase() + "%" : "%";
        return gameAccessoryRepository.findFiltered(subtype, grade, civilMask, hasSearch, searchPattern).stream()
                .map(a -> toAccessoryResponse(a, effectMap))
                .toList();
    }

    public GameAccessoryResponse getAccessory(String gameCode) {
        var accessory = gameAccessoryRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new AppException("Acessório não encontrado no catálogo.", HttpStatus.NOT_FOUND));
        return toAccessoryResponse(accessory, effectMap());
    }

    public List<GameArmorResponse> listArmor(
            String slot, Integer grade, String civilMask, Integer minLevel, String search) {
        var effectMap = effectMap();
        var normalizedSearch = blankToNull(search);
        var hasSearch = normalizedSearch != null;
        var searchPattern = hasSearch ? "%" + normalizedSearch.toLowerCase() + "%" : "%";
        int levelFloor = minLevel != null ? minLevel : 35;
        return gameArmorRepository
                .findFiltered(slot, grade, civilMask, levelFloor, hasSearch, searchPattern)
                .stream()
                .map(a -> toArmorResponse(a, effectMap))
                .toList();
    }

    public GameArmorResponse getArmor(String gameCode) {
        var armor = gameArmorRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new AppException("Armadura não encontrada no catálogo.", HttpStatus.NOT_FOUND));
        return toArmorResponse(armor, effectMap());
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

    public List<ItemSetResponse> listAllSets() {
        var effectMap = effectMap();
        return itemSetRepository.findAll().stream()
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

    private GameArmorResponse toArmorResponse(GameArmor a, Map<Integer, EffectDefinition> effectMap) {
        var effects = new ArrayList<GameAccessoryEffectResponse>();
        addAccessoryEffect(effects, effectMap, a.getEffCode1(), a.getEffUnit1());
        addAccessoryEffect(effects, effectMap, a.getEffCode2(), a.getEffUnit2());
        addAccessoryEffect(effects, effectMap, a.getEffCode3(), a.getEffUnit3());
        addAccessoryEffect(effects, effectMap, a.getEffCode4(), a.getEffUnit4());

        return new GameArmorResponse(
                a.getId(), a.getGameCode(), a.getName(), a.getSlot(),
                a.getIconId(), a.getSpriteSheet(), a.getSpriteCols(), a.getGrade(), a.getCivilMask(),
                a.getLevelRequired(), a.getDefFc(), a.getDefFacing(),
                DefFacingUtil.toClientDsr(a.getDefFacing()),
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
                code, name, displayType, rawValue, formatEffectValue(displayType, rawValue, code)));
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
                code, name, displayType, rawValue, formatEffectValue(displayType, rawValue, code)));
    }

    static String formatEffectValue(EffectDisplayType displayType, BigDecimal rawValue, Integer code) {
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
            case SEC_MILLIS:
                return formatSecDisplay(rawValue, code);
            default:
                return rawValue.toPlainString();
        }
    }

    /** RF stores time bonuses as millis-like units; launcher uses ÷2000 when |raw| ≥ 100. */
    static String formatSecDisplay(BigDecimal rawValue, Integer code) {
        var abs = rawValue.abs();
        int divisor = code != null && code == 25 && abs.compareTo(BigDecimal.valueOf(100)) >= 0 ? 2000 : 1000;
        var sec = abs.divide(BigDecimal.valueOf(divisor), 3, RoundingMode.HALF_UP).stripTrailingZeros();
        return sec.toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
