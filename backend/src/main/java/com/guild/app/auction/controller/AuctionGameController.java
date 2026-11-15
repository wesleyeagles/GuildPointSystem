package com.guild.app.auction.controller;

import com.guild.app.auction.service.AuctionGameService;
import com.guild.app.common.exception.AppException;
import com.guild.app.shout.service.GameLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Leilão dentro do jogo, pela DLL (sem login, liberado no SecurityConfig), pelo nome do personagem.
 * Respostas em text/plain UTF-8, um registro por linha e campos separados por TAB.
 */
@RestController
@RequestMapping("/api/auctions/game")
@RequiredArgsConstructor
public class AuctionGameController {

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final AuctionGameService service;
    private final GameLanguage language;

    /**
     * Uma linha por leilão aberto: "id&lt;TAB&gt;fim (s Unix)&lt;TAB&gt;lance atual&lt;TAB&gt;itens"; 204 = nenhum.
     * Com details=1 (DLL 1.43.2+), mais "&lt;TAB&gt;efeitos da relic&lt;TAB&gt;ícone (sprite|ícone|colunas)" (podem vir vazios).
     */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> open(@RequestParam(required = false) String character,
                                       @RequestParam(defaultValue = "0") int details) {
        var auctions = service.open(character);
        if (auctions.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(auctions.stream()
            .map(a -> a.id() + "\t" + a.endsAtEpoch() + "\t" + a.currentBid() + "\t" + a.items()
                + (details == 1 ? "\t" + a.effects() + "\t" + a.icon() : "") + "\n")
            .collect(Collectors.joining()));
    }

    /**
     * Lances superados (DLL 1.44.0+): "último lance&lt;TAB&gt;fim&lt;TAB&gt;lance atual&lt;TAB&gt;leilão&lt;TAB&gt;mínimo
     * &lt;TAB&gt;saldo livre&lt;TAB&gt;itens&lt;TAB&gt;efeitos&lt;TAB&gt;ícone"; 204 = nenhum.
     */
    @GetMapping(value = "/outbid", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> outbid(@RequestParam(required = false) String character) {
        var list = service.outbid(character);
        if (list.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(list.stream()
            .map(o -> o.bidId() + "\t" + o.endsAtEpoch() + "\t" + o.currentBid() + "\t" + o.auctionId() + "\t"
                + o.minBid() + "\t" + o.available() + "\t" + o.items() + "\t" + o.effects() + "\t" + o.icon() + "\n")
            .collect(Collectors.joining()));
    }

    /**
     * Janela de leilões: "B&lt;TAB&gt;saldo livre&lt;TAB&gt;agora (s Unix)" e uma linha "A" por leilão em andamento
     * (ver {@link #row}).
     */
    @GetMapping(value = "/list", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> list(@RequestParam(required = false) String character) {
        return text(character, () -> {
            var list = service.list(character);
            return "B\t" + list.available() + "\t" + now() + "\n"
                + list.auctions().stream().map(AuctionGameController::row).collect(Collectors.joining());
        });
    }

    /**
     * Um leilão: a linha "A", "B&lt;TAB&gt;saldo livre para ele&lt;TAB&gt;agora" e as últimas mensagens de lance e do
     * sistema (sem o chat), da mais nova à mais antiga: "M&lt;TAB&gt;quando (s Unix)&lt;TAB&gt;BID|BOT&lt;TAB&gt;texto".
     */
    @GetMapping(value = "/{id}", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> detail(@PathVariable long id, @RequestParam(required = false) String character) {
        return text(character, () -> {
            var d = service.detail(id, character);
            return row(d.auction()) + "B\t" + d.available() + "\t" + now() + "\n"
                + d.messages().stream().map(m -> "M\t" + m.atEpoch() + "\t" + m.type() + "\t" + m.text() + "\n")
                    .collect(Collectors.joining());
        });
    }

    /** Lance com o valor no corpo (text/plain). 200 = "valor&lt;TAB&gt;lance atual"; erro = status e o texto do site. */
    @PostMapping(value = "/{id}/bid", consumes = "text/plain", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> bid(@PathVariable long id, @RequestParam(required = false) String character,
                                      @RequestBody(required = false) String amount) {
        return text(character, () -> {
            var r = service.bid(id, character, amount);
            return r.currentBid() + "\t" + r.currentBid() + "\n";
        });
    }

    /**
     * "A&lt;TAB&gt;id&lt;TAB&gt;status&lt;TAB&gt;fim&lt;TAB&gt;lance atual&lt;TAB&gt;mínimo&lt;TAB&gt;meu (0 não participo,
     * 1 superado, 2 ganhando)&lt;TAB&gt;quem lidera&lt;TAB&gt;itens&lt;TAB&gt;efeitos&lt;TAB&gt;ícone".
     */
    private static String row(AuctionGameService.Row r) {
        return "A\t" + r.id() + "\t" + r.status() + "\t" + r.endsAtEpoch() + "\t" + r.currentBid() + "\t" + r.minBid()
            + "\t" + r.mine() + "\t" + r.leader() + "\t" + r.items() + "\t" + r.effects() + "\t" + r.icon() + "\n";
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private ResponseEntity<String> text(String character, Supplier<String> body) {
        try {
            return ResponseEntity.ok().contentType(TEXT).body(body.get());
        } catch (AppException e) {
            return ResponseEntity.status(e.getStatus()).contentType(TEXT).body(language.text(character, e.getMessage()));
        }
    }
}
