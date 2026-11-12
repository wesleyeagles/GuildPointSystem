package com.guild.app.shout.controller;

import com.guild.app.dll.service.DllReleaseService;
import com.guild.app.shout.service.GameLanguage;
import com.guild.app.shout.service.ShoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Shout no jogo, todo pela DLL (sem login, liberado no SecurityConfig), pelo nome do personagem.
 * Textos em UTF-8; a mensagem vai no corpo como text/plain.
 */
@RestController
@RequestMapping("/api/shout/game")
@RequiredArgsConstructor
public class ShoutController {

    /** Última versão da DLL publicada com atualização automática (lido pela DLL 1.33.0+, site_http.hpp). */
    public static final String LATEST_HEADER = "X-Cerberus-Latest";

    private final ShoutService service;
    private final DllReleaseService dllReleases;
    private final GameLanguage language;

    /**
     * "id&lt;TAB&gt;autor&lt;TAB&gt;mensagem"; 204 = nenhum shout ativo para este personagem. Toda DLL no mundo
     * pergunta a cada 5 s: a resposta leva também a última versão da DLL, para atualizar na hora, e o idioma
     * do personagem ({@link GameLanguage#HEADER}: "en" = tag "English Player", lido pela DLL 1.39.0+).
     */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> current(@RequestParam(required = false) String character) {
        HttpHeaders headers = new HttpHeaders();
        String latest = dllReleases.announcedVersion();
        if (!latest.isEmpty()) headers.set(LATEST_HEADER, latest);
        if (character != null && !character.isBlank()) headers.set(GameLanguage.HEADER, language.code(character));
        return service.forGame(character)
            .map(s -> ResponseEntity.ok().headers(headers).body(s.id() + "\t" + s.author() + "\t" + s.message() + "\n"))
            .orElseGet(() -> ResponseEntity.noContent().headers(headers).build());
    }

    /** Se a DLL mostra o botão de shout: 204 = personagem de Líder/Administrador, 404 = não. */
    @GetMapping("/sender")
    public ResponseEntity<Void> sender(@RequestParam(required = false) String character) {
        return service.sender(character).isPresent()
            ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Envia o shout: 204 = enviado; 403 sem permissão, 400 mensagem inválida, 409 aguarde. */
    @PostMapping(consumes = "text/plain")
    public ResponseEntity<Void> send(@RequestParam(required = false) String character,
                                     @RequestBody(required = false) String message) {
        service.send(character, message);
        return ResponseEntity.noContent().build();
    }
}
