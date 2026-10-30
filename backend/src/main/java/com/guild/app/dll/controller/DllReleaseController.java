package com.guild.app.dll.controller;

import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.dto.DllUpdateManifest;
import com.guild.app.dll.dto.MemberDllView;
import com.guild.app.dll.service.DllReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/dll")
@RequiredArgsConstructor
public class DllReleaseController {

    private final DllReleaseService service;

    /** Versao mais recente (204 enquanto nada foi publicado). */
    @GetMapping("/latest")
    public ResponseEntity<DllReleaseView> latest() {
        return service.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Download da versao mais recente. Publico (SecurityConfig): o link e aberto sem o token. */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        return service.latestFile()
                .map(release -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(release.getFileName(), StandardCharsets.UTF_8).build().toString())
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                        .body(release.getFileData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/releases")
    public List<DllReleaseView> releases() {
        return service.releases();
    }

    /**
     * {@code dll} + {@code signature} (version.dll e version.dll.sig do dll-signing.ps1) ligam a
     * atualizacao automatica; {@code file} (.rar/.zip) e opcional nesse caso.
     */
    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.CREATED)
    public DllReleaseView publish(@RequestParam("version") String version,
                                  @RequestParam(value = "notes", required = false) String notes,
                                  @RequestParam(value = "file", required = false) MultipartFile file,
                                  @RequestParam(value = "dll", required = false) MultipartFile dll,
                                  @RequestParam(value = "signature", required = false) MultipartFile signature)
            throws IOException {
        String signatureText = signature == null || signature.isEmpty() || signature.getSize() > 1024
                ? null : new String(signature.getBytes(), StandardCharsets.US_ASCII);
        return service.publish(version, notes, file, dll, signatureText);
    }

    /** Publico: a DLL instalada pergunta se ha versao nova (204 = nenhuma com atualizacao automatica). */
    @GetMapping("/update")
    public ResponseEntity<DllUpdateManifest> updateManifest() {
        return service.updateManifest()
                .map(manifest -> ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-cache").body(manifest))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Publico: o version.dll assinado (a DLL confere sha256 e assinatura antes de usar). */
    @GetMapping("/update/file")
    public ResponseEntity<byte[]> updateFile(@RequestParam("v") String version) {
        return service.updateFile(version)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/members")
    public List<MemberDllView> members() {
        return service.members();
    }
}
