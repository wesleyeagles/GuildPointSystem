package com.guild.app.quest;

import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.quest.dto.QuestStatusIngestRequest;
import com.guild.app.quest.repository.QuestStatusRepository;
import com.guild.app.quest.service.QuestStatusService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestStatusNotificationsTest {
    @Mock QuestStatusRepository statuses;
    @Mock MemberRepository members;
    @Mock CharacterStatusRepository characters;
    @Mock WebSocketPublisher publisher;
    @InjectMocks QuestStatusService service;

    @Test void incomingCompletionsNotifyEveryAccountOfTheCharacter() {
        var first = new Member(); first.setId(11L);
        var shared = new Member(); shared.setId(22L);
        when(members.findByGameCharacter("Player")).thenReturn(List.of(first, shared));
        when(members.findByIdForUpdate(11L)).thenReturn(Optional.of(first));
        when(members.findByIdForUpdate(22L)).thenReturn(Optional.of(shared));

        service.ingest(new QuestStatusIngestRequest("Player", List.of(), List.of(0x071396, 0x071397)));

        verify(publisher).publishQuestChanged(11L);
        verify(publisher).publishQuestChanged(22L);
        verifyNoMoreInteractions(publisher);
    }

    @Test void failedWriteDoesNotNotifyClients() {
        var member = new Member(); member.setId(11L);
        when(members.findByGameCharacter("Player")).thenReturn(List.of(member));
        when(members.findByIdForUpdate(11L)).thenReturn(Optional.of(member));
        when(statuses.save(any())).thenThrow(new IllegalStateException("write failed"));

        assertThrows(IllegalStateException.class, () -> service.ingest(
                new QuestStatusIngestRequest("Player", List.of(), List.of(0x071396))));

        verifyNoInteractions(publisher);
    }

    @Test void questNoticesArePrivateAndOnlySentAfterCommit() {
        var template = mock(SimpMessagingTemplate.class);
        var realPublisher = new WebSocketPublisher(template);
        TransactionSynchronizationManager.initSynchronization();
        try {
            realPublisher.publishQuestChanged(11L);
            verifyNoInteractions(template);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(template).convertAndSend("/topic/quests/member/11", Map.of("changed", true));
            verifyNoMoreInteractions(template);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
