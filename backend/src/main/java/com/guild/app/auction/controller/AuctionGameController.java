package com.guild.app.auction.controller;

import com.guild.app.auction.service.AuctionGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * Aviso de leilão aberto dentro do jogo, pela DLL (sem login, liberado no SecurityConfig), pelo nome do
 * personagem. Resposta em text/plain UTF-8.
 */
@RestController
@RequestMapping("/api/auctions/game")
@RequiredArgsConstructor
public class AuctionGameController {

    private final AuctionGameService service;

    /** Uma linha por leilão aberto: "id&lt;TAB&gt;fim (s Unix)&lt;TAB&gt;lance atual&lt;TAB&gt;itens"; 204 = nenhum. */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> open(@RequestParam(required = false) String character) {
        var auctions = service.open(character);
        if (auctions.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(auctions.stream()
            .map(a -> a.id() + "\t" + a.endsAtEpoch() + "\t" + a.currentBid() + "\t" + a.items() + "\n")
            .collect(Collectors.joining()));
    }
}
