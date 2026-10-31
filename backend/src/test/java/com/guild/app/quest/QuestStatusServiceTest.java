package com.guild.app.quest;

import com.guild.app.quest.dto.QuestStatusIngestRequest;
import com.guild.app.quest.repository.QuestStatusRepository;
import com.guild.app.quest.service.QuestStatusService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuestStatusServiceTest {
    @Autowired QuestStatusService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired QuestStatusRepository statuses;
    long memberId;
    String nick;
    // Q071356 / Q082000 / Q082010 em inteiro.
    static final int A = 0x071356, B = 0x082000, C = 0x082010;

    @BeforeEach void setup() {
        nick = "Qst" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        memberId = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','APROVADO',true) RETURNING id",
            Long.class, nick);
    }
    void send(List<Integer> active, List<Integer> done) { service.ingest(new QuestStatusIngestRequest(nick, active, done)); }
    Set<String> done() { return service.doneCodesForDay(memberId, QuestStatusService.currentQuestDay()); }

    @Test void oneBatchPreservesEachQuestCompletedByTheSameKill() {
        var codes=List.of(0x071396,0x071397,0x071398,0x071399,0x071400,0x071401,0x071402,0x071403,0x071404,0x071405,0x071406);
        send(codes,List.of());
        send(List.of(),codes);
        assertEquals(codes.size(),done().size());
        send(List.of(),codes); // reenvio do mesmo lote não duplica nem reduz o total
        assertEquals(codes.size(),done().size());
        assertTrue(done().containsAll(Set.of("Q071396","Q071397","Q071406")));
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentCompletionBatchesKeepAllQuestCodes() throws Exception {
        send(List.of(A,B,C),List.of());
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(3)) {
            var tasks=new ArrayList<Future<?>>();
            for(int code:List.of(A,B,C)) tasks.add(executor.submit(()->{
                try { start.await(); send(List.of(),List.of(code)); }
                catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            start.countDown();
            for(var task:tasks) task.get(20,TimeUnit.SECONDS);
        }
        assertEquals(Set.of("Q071356","Q082000","Q082010"),done());
    }

    @Test void explicitCompletionsWinWhenTheJournalStillContainsTheSameQuests() {
        var codes=List.of(0x071396,0x071397,0x071398);
        send(codes,List.of());
        send(codes,codes);
        assertEquals(Set.of("Q071396","Q071397","Q071398"),done());
        assertEquals("",statuses.findById(memberId).orElseThrow().getActiveCodes());
        send(List.of(0x071397),List.of()); // reaceitar depois continua sendo uma nova execução
        assertEquals(Set.of("Q071396","Q071398"),done());
    }

    @Test void reopeningTheGameKeepsTheQuestsAlreadyDoneToday() {
        send(List.of(C), List.of(A, B));
        // Jogo reaberto: a DLL volta sem memória e manda done vazio.
        send(List.of(C), List.of());
        assertEquals(Set.of("Q071356", "Q082000"), done());
        send(List.of(), List.of(C));
        assertEquals(Set.of("Q071356", "Q082000", "Q082010"), done());
    }
    @Test void aQuestActiveAgainStopsCountingAsDone() {
        send(List.of(), List.of(A, B));
        send(List.of(A), List.of());
        assertEquals(Set.of("Q082000"), done());
    }
    @Test void aNewQuestDayStartsEmpty() {
        send(List.of(), List.of(A));
        var status = statuses.findById(memberId).orElseThrow();
        status.setQuestDay(status.getQuestDay().minusDays(1)); // simula a virada das 03h
        statuses.saveAndFlush(status);
        send(List.of(), List.of(B));
        assertEquals(Set.of("Q082000"), done());
    }
    @Test void sharedCharacterUpdatesEveryAccountWithThatNick() {
        long sharedId = jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','APROVADO',true) RETURNING id",
            Long.class, nick.toLowerCase());
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO','EXCLUIDO',true)", nick);
        send(List.of(), List.of(A));
        assertEquals(Set.of("Q071356"), done());
        assertEquals(Set.of("Q071356"), service.doneCodesForDay(sharedId, QuestStatusService.currentQuestDay()));
    }
}
