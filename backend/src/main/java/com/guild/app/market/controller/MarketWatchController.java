package com.guild.app.market.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.item.service.GameItemCard;
import com.guild.app.market.service.MarketWatchService;
import com.guild.app.market.service.MarketWatchService.Alert;
import com.guild.app.market.service.MarketWatchService.CatalogItem;
import com.guild.app.market.service.MarketWatchService.NewWatch;
import com.guild.app.market.service.MarketWatchService.View;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alertas de item no market da Cerberus. O site (Admin/Líder) monta a lista membro -> item; a DLL do membro
 * busca os alertas pelo nome do personagem (sem login, liberado no SecurityConfig) e mostra o card no jogo.
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketWatchController {
    private final MarketWatchService service;
    private final GameItemCard cards;

    /**
     * Uma linha por alerta dos últimos 30 min, do mais novo ao mais antigo:
     * "id TAB some em (s Unix) TAB preço total (CS) TAB código TAB item" (item = nome, " +N" e " xN"). 204 = nenhum.
     * Com details=1 (DLL 1.43.2+), mais "TAB efeitos da relic TAB ícone (sprite|ícone|colunas)" (podem vir vazios).
     */
    @GetMapping(value = "/game/alerts", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> gameAlerts(@RequestParam(required = false) String character,
                                             @RequestParam(defaultValue = "0") int details) {
        List<Alert> alerts = service.gameAlerts(character);
        if (alerts.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(alerts.stream()
            .map(a -> a.id() + "\t" + a.createdAt().plus(MarketWatchService.GAME_WINDOW).getEpochSecond() + "\t" + a.price()
                + "\t" + a.itemCode() + "\t" + label(a)
                + (details == 1 ? "\t" + cards.effects(a.itemCode()) + "\t" + cards.iconKey(a.itemCode()) : "") + "\n")
            .collect(Collectors.joining()));
    }

    static String label(Alert a) {
        String text = a.itemName().replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").strip();
        if (a.upgrade() > 0) text += " +" + a.upgrade();
        if (a.quantity() > 1) text += " x" + a.quantity();
        return text;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/watches")
    public View watches() { return service.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/watches")
    public View add(@RequestBody NewWatch body) { return service.add(body, SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/watches/{id}")
    public View remove(@PathVariable long id) { return service.remove(id, SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/watches/items")
    public List<CatalogItem> items(@RequestParam(required = false) String search) {
        return service.catalog(search, SecurityUtils.currentMember());
    }
}
