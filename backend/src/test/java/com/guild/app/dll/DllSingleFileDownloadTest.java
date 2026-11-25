package com.guild.app.dll;

import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.entity.DllRelease;
import com.guild.app.dll.repository.DllReleaseRepository;
import com.guild.app.dll.service.DllReleaseService;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DllSingleFileDownloadTest {
    @Test void signedReleaseDownloadsOnlyDllWithoutChangingStoredPackageOrUpdater() throws Exception {
        var repo=mock(DllReleaseRepository.class);
        var release=new DllRelease();
        release.setVersion("1.52.3");release.setFileName("original.zip");
        byte[] original={1,2,3}, dll={77,90,5,6};
        release.setFileData(original);release.setUpdateData(dll);
        release.setUpdateSignature("existing-signature");release.setUpdateSha256("existing-sha256");
        when(repo.findAllViews()).thenReturn(List.of(new DllReleaseView(1L,"1.52.3","minor fixes","original.zip",3,"Admin",Instant.EPOCH,true)));
        when(repo.findById(1L)).thenReturn(Optional.of(release));
        var service=new DllReleaseService(mock(DllSignatureVerifier.class),repo,mock(CharacterStatusRepository.class),mock(MemberRepository.class),mock(AuditLogService.class),mock(WebSocketPublisher.class));
        var download=service.latestFile().orElseThrow();
        assertEquals("CerberusBags-1.52.3.zip",download.getFileName());
        try(var zip=new ZipInputStream(new ByteArrayInputStream(download.getFileData()))) {
            assertEquals("version.dll",zip.getNextEntry().getName());
            assertArrayEquals(dll,zip.readAllBytes());
            assertNull(zip.getNextEntry());
        }
        assertTrue(new String(download.getFileData(),StandardCharsets.ISO_8859_1).contains("MinGW-w64 runtime licensing"));
        assertArrayEquals(original,release.getFileData());assertArrayEquals(dll,release.getUpdateData());
        assertEquals("existing-signature",release.getUpdateSignature());assertEquals("existing-sha256",release.getUpdateSha256());
        verify(repo,never()).save(any());
        release.setUpdateData(null);
        assertSame(release,service.latestFile().orElseThrow());
        assertArrayEquals(original,service.latestFile().orElseThrow().getFileData());
    }
    public static void main(String[] args) throws Exception {
        new DllSingleFileDownloadTest().signedReleaseDownloadsOnlyDllWithoutChangingStoredPackageOrUpdater();
        System.out.println("PASS one DLL, original bytes, archive notices, unchanged persisted package/signature/hash and legacy fallback");
    }
}
