package com.guild.app.characterstatus;

import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.characterstatus.service.SyncAccessService;
import com.guild.app.dll.service.DllReleaseService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncAccessServiceTest {

    private final CharacterStatusRepository repository = mock(CharacterStatusRepository.class);
    private final Instant now = Instant.now();

    private SyncAccessService service(Instant enforceFrom) {
        return new SyncAccessService(repository, mock(DllReleaseService.class), enforceFrom.toString().replace("Z", "+00:00"),
                Duration.ofDays(3), Duration.ofDays(1));
    }

    @Test
    void beforeDeadlineNobodyIsBlocked() {
        when(repository.findLastSyncAt(1L)).thenReturn(Optional.empty());
        var service = service(now.plus(Duration.ofDays(2)));

        assertThat(service.isBlocked(1L, now.minus(Duration.ofDays(30)))).isFalse();
        var status = service.status(1L, now.minus(Duration.ofDays(30)));
        assertThat(status.synced()).isFalse();
        assertThat(status.blocked()).isFalse();
        assertThat(status.newAccount()).isFalse();
    }

    @Test
    void afterDeadlineOnlyRecentSyncPasses() {
        var service = service(now.minus(Duration.ofHours(1)));
        Instant oldAccount = now.minus(Duration.ofDays(30));

        when(repository.findLastSyncAt(1L)).thenReturn(Optional.of(now.minus(Duration.ofDays(2))));
        when(repository.findLastSyncAt(2L)).thenReturn(Optional.of(now.minus(Duration.ofDays(4))));
        when(repository.findLastSyncAt(3L)).thenReturn(Optional.empty());

        assertThat(service.isBlocked(1L, oldAccount)).isFalse();
        assertThat(service.isBlocked(2L, oldAccount)).isTrue();
        assertThat(service.isBlocked(3L, oldAccount)).isTrue();
    }

    @Test
    void newAccountGetsOneDayFromSignupAfterDeadline() {
        when(repository.findLastSyncAt(1L)).thenReturn(Optional.empty());
        var service = service(now.minus(Duration.ofDays(5)));

        Instant createdAt = now.minus(Duration.ofHours(10));
        var status = service.status(1L, createdAt);
        assertThat(status.newAccount()).isTrue();
        assertThat(status.deadline()).isEqualTo(createdAt.plus(Duration.ofDays(1)));
        assertThat(status.blocked()).isFalse();
        assertThat(service.isBlocked(1L, createdAt)).isFalse();

        assertThat(service.isBlocked(1L, now.minus(Duration.ofHours(25)))).isTrue();
    }

    private void synced(long memberId, String version) {
        when(repository.findLastSyncAt(memberId)).thenReturn(Optional.of(now.minus(Duration.ofHours(1))));
        var status = new CharacterStatus();
        status.setDllVersion(version);
        when(repository.findFirstByMemberIdOrderByUpdatedAtDesc(memberId)).thenReturn(Optional.of(status));
    }

    @Test
    void dllBelowMinimumIsRefusedEvenWhenSynced() {
        var service = service(now.minus(Duration.ofDays(1)));
        synced(1L, "1.1.9");
        synced(2L, "1.2.0");
        synced(3L, "1.7.0");
        synced(4L, null);                       // DLL antiga, sem versão no User-Agent
        when(repository.findLastSyncAt(5L)).thenReturn(Optional.empty());  // nunca sincronizou: outro bloqueio

        assertThat(service.isDllTooOld(1L)).isTrue();
        assertThat(service.isDllTooOld(2L)).isFalse();
        assertThat(service.isDllTooOld(3L)).isFalse();
        assertThat(service.isDllTooOld(4L)).isTrue();
        assertThat(service.isDllTooOld(5L)).isFalse();

        var status = service.status(1L, now.minus(Duration.ofDays(30)));
        assertThat(status.dllTooOld()).isTrue();
        assertThat(status.dllVersion()).isEqualTo("1.1.9");
        assertThat(status.minDllVersion()).isEqualTo("1.2.0");
    }

    @Test
    void updatingTheDllUnblocksImmediately() {
        var service = service(now.minus(Duration.ofDays(1)));
        synced(1L, "1.0.0");
        assertThat(service.isDllTooOld(1L)).isTrue();
        service.recordSync(1L, Instant.now(), "1.7.0");
        assertThat(service.isDllTooOld(1L)).isFalse();
    }

    @Test
    void ingestUnblocksImmediately() {
        when(repository.findLastSyncAt(1L)).thenReturn(Optional.empty());
        var service = service(now.minus(Duration.ofDays(1)));
        Instant oldAccount = now.minus(Duration.ofDays(30));

        assertThat(service.isBlocked(1L, oldAccount)).isTrue();
        service.recordSync(1L, Instant.now());
        assertThat(service.isBlocked(1L, oldAccount)).isFalse();
    }
}
