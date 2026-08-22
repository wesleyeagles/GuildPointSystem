package com.guild.app.common.controller;

import com.guild.app.common.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadPath;

    public UploadController(@Value("${guild.upload.base-path}") String basePath) throws IOException {
        this.uploadPath = Path.of(basePath);
        Files.createDirectories(uploadPath);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "item") String type) throws IOException {

        if (file.isEmpty()) {
            throw new AppException("Selecione um arquivo.", HttpStatus.BAD_REQUEST);
        }

        long maxSize = switch (type) {
            case "avatar", "item" -> 2 * 1024 * 1024;
            case "chat" -> 1024 * 1024;
            default -> 2 * 1024 * 1024;
        };

        if (file.getSize() > maxSize) {
            throw new AppException("O arquivo é muito grande.", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new AppException("Tipo de arquivo inválido. Use JPG, PNG ou WebP.", HttpStatus.BAD_REQUEST);
        }

        String ext = contentType.replace("image/", "");
        String filename = UUID.randomUUID() + "." + ext;
        Path target = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), target);

        return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
    }
}
