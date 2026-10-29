package com.guild.app.dll.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.dll.DllVersion;
import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.dto.MemberDllView;
import com.guild.app.dll.entity.DllRelease;
import com.guild.app.dll.repository.DllReleaseRepository;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Versoes da DLL publicadas pela administracao. A mais recente e a que o site oferece para
 * download; quem sincroniza com uma anterior recebe o aviso de desatualizado (nao bloqueia).
 */
@Service
@RequiredArgsConstructor
public class DllReleaseService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final List<String> EXTENSIONS = List.of(".rar", ".zip");

    private final DllReleaseRepository releaseRepository;
    private final CharacterStatusRepository statusRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;
    private final WebSocketPublisher webSocketPublisher;

    @Transactional(readOnly = true)
    public Optional<DllReleaseView> latest() {
        return releaseRepository.findAllViews().stream().findFirst();
    }

    /** Arquivo da versao mais recente (download publico: a DLL e distribuida para todos). */
    @Transactional(readOnly = true)
    public Optional<DllRelease> latestFile() {
        return latest().flatMap(view -> releaseRepository.findById(view.id()))
                .map(release -> {
                    release.getFileData(); // carrega os bytes dentro da transacao
                    return release;
                });
    }

    /** Desatualizado: sincroniza, existe versao publicada e a dele e anterior (ou sem versao). */
    public static boolean isOutdated(String memberVersion, String latestVersion) {
        if (latestVersion == null) return false;
        return memberVersion == null || DllVersion.compare(memberVersion, latestVersion) < 0;
    }

    @Transactional(readOnly = true)
    public List<DllReleaseView> releases() {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        return releaseRepository.findAllViews();
    }

    @Transactional
    public DllReleaseView publish(String version, String notes, MultipartFile file) throws IOException {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        MemberPrincipal actor = SecurityUtils.currentMember();

        String cleanVersion = version == null ? "" : version.trim();
        if (!DllVersion.isValid(cleanVersion)) {
            throw new AppException("Versão inválida. Use números separados por ponto, ex.: 1.2.0.", HttpStatus.BAD_REQUEST);
        }
        if (releaseRepository.existsByVersion(cleanVersion)) {
            throw new AppException("A versão " + cleanVersion + " já foi publicada.", HttpStatus.CONFLICT);
        }
        Optional<DllReleaseView> current = latest();
        if (current.isPresent() && DllVersion.compare(cleanVersion, current.get().version()) <= 0) {
            throw new AppException("A nova versão precisa ser maior que a atual (" + current.get().version() + ").",
                    HttpStatus.BAD_REQUEST);
        }
        if (file == null || file.isEmpty()) {
            throw new AppException("Selecione o arquivo da DLL (.rar ou .zip).", HttpStatus.BAD_REQUEST);
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new AppException("Envie a DLL compactada em .rar ou .zip.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException("O arquivo passa de 5 MB.", HttpStatus.BAD_REQUEST);
        }

        DllRelease release = new DllRelease();
        release.setVersion(cleanVersion);
        release.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        release.setFileName(fileName.length() > 120 ? fileName.substring(fileName.length() - 120) : fileName);
        release.setFileData(file.getBytes());
        release.setFileSize((int) file.getSize());
        release.setPublishedBy(memberRepository.getReferenceById(actor.getId()));
        release = releaseRepository.save(release);

        DllReleaseView view = new DllReleaseView(release.getId(), release.getVersion(), release.getNotes(),
                release.getFileName(), release.getFileSize(), actor.getNickname(), release.getPublishedAt());
        auditLogService.log(AuditLogType.DLL_RELEASED, actor, null,
                Map.of("version", cleanVersion, "fileName", release.getFileName()));
        // Todo mundo online recebe o aviso de nova versao na hora (apos o commit).
        webSocketPublisher.publishDllRelease(view);
        return view;
    }

    /** Versao da DLL de cada membro aprovado, pelo envio mais recente de qualquer personagem. */
    @Transactional(readOnly = true)
    public List<MemberDllView> members() {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        String latestVersion = latest().map(DllReleaseView::version).orElse(null);

        Map<Long, CharacterStatus> lastByMember = new HashMap<>();
        for (CharacterStatus status : statusRepository.findAll()) {
            lastByMember.merge(status.getMember().getId(), status,
                    (a, b) -> a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a : b);
        }
        return memberRepository.findByStatusOrderByPointsDesc(MemberStatus.APROVADO).stream()
                .map(member -> toView(member, lastByMember.get(member.getId()), latestVersion))
                .sorted(Comparator.comparing(MemberDllView::nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static MemberDllView toView(Member member, CharacterStatus last, String latestVersion) {
        String version = last != null ? last.getDllVersion() : null;
        return new MemberDllView(member.getId(), member.getNickname(), version,
                last != null ? last.getUpdatedAt() : null,
                last != null && isOutdated(version, latestVersion));
    }
}
