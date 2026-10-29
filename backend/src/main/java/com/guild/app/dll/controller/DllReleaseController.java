package com.guild.app.dll.controller;

import com.guild.app.dll.dto.DllReleaseView;
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

    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.CREATED)
    public DllReleaseView publish(@RequestParam("version") String version,
                                  @RequestParam(value = "notes", required = false) String notes,
                                  @RequestParam("file") MultipartFile file) throws IOException {
        return service.publish(version, notes, file);
    }

    @GetMapping("/members")
    public List<MemberDllView> members() {
        return service.members();
    }
}
