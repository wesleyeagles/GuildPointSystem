package com.guild.app.dll.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.dll.DllSignatureVerifier;
import com.guild.app.dll.DllVersion;
import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.dto.DllUpdateManifest;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    private static final long MAX_DLL_SIZE = 4L * 1024 * 1024;
    private static final List<String> EXTENSIONS = List.of(".rar", ".zip");

    private final DllSignatureVerifier signatureVerifier;
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

    /** Manifesto da atualizacao automatica mais recente (rota publica, lida pela DLL). */
    @Transactional(readOnly = true)
    public Optional<DllUpdateManifest> updateManifest() {
        return releaseRepository.findUpdateManifests().stream().findFirst();
    }

    /** version.dll assinado de uma versao com atualizacao automatica. */
    @Transactional(readOnly = true)
    public Optional<byte[]> updateFile(String version) {
        return releaseRepository.findByVersion(version).map(DllRelease::getUpdateData);
    }

    /** Publicacao so com o pacote (.rar/.zip), sem atualizacao automatica. */
    @Transactional
    public DllReleaseView publish(String version, String notes, MultipartFile file) throws IOException {
        return publish(version, notes, file, null, null);
    }

    /**
     * Publica uma versao. Com {@code dll} + {@code signature} (tools/dll-signing.ps1) as DLLs
     * instaladas se atualizam sozinhas; o pacote para download manual e opcional (o site gera
     * um .zip com o version.dll). So com {@code file}: como antes, sem atualizacao automatica.
     */
    @Transactional
    public DllReleaseView publish(String version, String notes, MultipartFile file, MultipartFile dll,
                                  String signature) throws IOException {
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
        boolean hasPackage = file != null && !file.isEmpty();
        boolean hasDll = dll != null && !dll.isEmpty();
        if (!hasPackage && !hasDll) {
            throw new AppException("Envie o version.dll assinado (atualização automática) ou o pacote .rar/.zip.",
                    HttpStatus.BAD_REQUEST);
        }

        DllRelease release = new DllRelease();
        release.setVersion(cleanVersion);
        release.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        if (hasDll) attachSignedDll(release, cleanVersion, dll, signature);

        if (hasPackage) {
            String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (EXTENSIONS.stream().noneMatch(lower::endsWith)) {
                throw new AppException("Envie a DLL compactada em .rar ou .zip.", HttpStatus.BAD_REQUEST);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new AppException("O arquivo passa de 5 MB.", HttpStatus.BAD_REQUEST);
            }
            release.setFileName(fileName.length() > 120 ? fileName.substring(fileName.length() - 120) : fileName);
            release.setFileData(file.getBytes());
        } else {
            // Download manual: .zip com o mesmo version.dll assinado.
            release.setFileName("CerberusBags-" + cleanVersion + ".zip");
            release.setFileData(zipDll(release.getUpdateData()));
        }
        release.setFileSize(release.getFileData().length);
        release.setPublishedBy(memberRepository.getReferenceById(actor.getId()));
        release = releaseRepository.save(release);

        DllReleaseView view = new DllReleaseView(release.getId(), release.getVersion(), release.getNotes(),
                release.getFileName(), release.getFileSize(), actor.getNickname(), release.getPublishedAt(), hasDll);
        auditLogService.log(AuditLogType.DLL_RELEASED, actor, null,
                Map.of("version", cleanVersion, "fileName", release.getFileName(), "autoUpdate", hasDll));
        // Todo mundo online recebe o aviso de nova versao na hora (apos o commit).
        webSocketPublisher.publishDllRelease(view);
        return view;
    }

    /**
     * Confere o version.dll antes de aceitar: DLL x86 com "CerberusBags/<versao>" embutido e
     * assinatura valida. O que a DLL instalada recusaria, o site recusa na publicacao.
     */
    private void attachSignedDll(DllRelease release, String version, MultipartFile dll, String signature)
            throws IOException {
        if (dll.getSize() > MAX_DLL_SIZE) {
            throw new AppException("O version.dll passa de 4 MB.", HttpStatus.BAD_REQUEST);
        }
        byte[] bytes = dll.getBytes();
        if (!isX86Dll(bytes)) {
            throw new AppException("O arquivo enviado não é um version.dll (DLL x86).", HttpStatus.BAD_REQUEST);
        }
        if (!containsUserAgent(bytes, version)) {
            throw new AppException("O version.dll não é da versão " + version
                    + " (não tem CerberusBags/" + version + " embutido).", HttpStatus.BAD_REQUEST);
        }
        if (signature == null || signature.isBlank()) {
            throw new AppException("Envie a assinatura (version.dll.sig) gerada pelo dll-signing.ps1.",
                    HttpStatus.BAD_REQUEST);
        }
        String sha256 = DllSignatureVerifier.sha256Hex(bytes);
        if (!signatureVerifier.verify(version, sha256, signature)) {
            throw new AppException("Assinatura inválida para este version.dll e esta versão. Assine de novo com "
                    + "dll-signing.ps1 -Sign -Version " + version + ".", HttpStatus.BAD_REQUEST);
        }
        release.setUpdateData(bytes);
        release.setUpdateSize(bytes.length);
        release.setUpdateSha256(sha256);
        release.setUpdateSignature(signature.trim());
    }

    static boolean isX86Dll(byte[] dll) {
        if (dll.length < 0x400 || dll[0] != 'M' || dll[1] != 'Z') return false;
        int pe = ByteBuffer.wrap(dll, 0x3C, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (pe < 0 || pe > dll.length - 24 || dll[pe] != 'P' || dll[pe + 1] != 'E' || dll[pe + 2] != 0 || dll[pe + 3] != 0) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(dll).order(ByteOrder.LITTLE_ENDIAN);
        return (header.getShort(pe + 4) & 0xFFFF) == 0x14C && (header.getShort(pe + 22) & 0x2000) != 0;
    }

    /** User-Agent UTF-16 com terminador: "1.2" nao casa com "1.2.0". */
    static boolean containsUserAgent(byte[] dll, String version) {
        byte[] ua = ("CerberusBags/" + version + "\0").getBytes(StandardCharsets.UTF_16LE);
        outer:
        for (int i = 0; i + ua.length <= dll.length; i++) {
            for (int j = 0; j < ua.length; j++) if (dll[i + j] != ua[j]) continue outer;
            return true;
        }
        return false;
    }

    private static byte[] zipDll(byte[] dll) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("version.dll"));
            zip.write(dll);
            zip.closeEntry();
        }
        return out.toByteArray();
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
