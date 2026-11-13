package com.guild.app.market.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.market.service.MarketPriceService;
import com.guild.app.market.service.MarketPriceService.AccessView;
import com.guild.app.market.service.MarketPriceService.Price;
import com.guild.app.market.service.MarketPriceService.Prices;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Preço do market para o tooltip de item da DLL (sem login, liberado no SecurityConfig; pelo nome do personagem).
 * Texto simples, fácil de ler em C++:
 *   linha 1: há quantos segundos o market foi lido
 *   depois, uma linha por upgrade com anúncio: upgrade TAB menor TAB média TAB anúncios  (preço por unidade, CS)
 * Só a linha 1 = ninguém vendendo agora. Só para personagem da lista (o Líder monta em Admin -> DLL).
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketPriceController {
    private final MarketPriceService service;
    Clock clock = Clock.systemUTC();

    public record AccessRequest(@Size(max = 500) List<Long> memberIds) {}

    /** 204 = personagem liberado (a DLL liga o preço no tooltip), 404 = não. */
    @GetMapping("/game/access")
    public ResponseEntity<Void> gameAccess(@RequestParam(required = false) String character) {
        return service.user(character).isPresent() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/game/price", produces = "text/plain;charset=UTF-8")
    public String gamePrice(@RequestParam(required = false) String character, @RequestParam(required = false) String code) {
        Prices prices = service.prices(character, code);
        StringBuilder out = new StringBuilder();
        out.append(Math.max(0, Duration.between(prices.readAt(), clock.instant()).toSeconds())).append('\n');
        for (Price p : prices.byUpgrade())
            out.append(p.upgrade()).append('\t').append(p.min().toPlainString()).append('\t')
               .append(p.avg().toPlainString()).append('\t').append(p.count()).append('\n');
        return out.toString();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/access")
    public AccessView access() { return service.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/access")
    public AccessView updateAccess(@Valid @RequestBody AccessRequest body) {
        return service.update(body.memberIds(), SecurityUtils.currentMember());
    }
}
