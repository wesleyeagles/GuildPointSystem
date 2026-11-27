package com.guild.app.resource;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.resource.service.ResourceSpawnService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceRetryTest {
    @ParameterizedTest
    @CsvSource({"2,Heart of Caves", "10,Heart of Ice", "11,Heart of Sand"})
    void failedInsertDoesNotSuppressRetry(int map, String name) {
        var attempts = new AtomicInteger();
        var jdbc = mock(JdbcTemplate.class, invocation -> {
            String sql = (String) invocation.getArgument(0);
            if (invocation.getMethod().getName().equals("queryForObject")) {
                if (sql.startsWith("SELECT tier")) return 3;
                if (sql.startsWith("SELECT revision")) return 0L;
                if (sql.startsWith("SELECT EXISTS")) return false;
            }
            if (invocation.getMethod().getName().equals("update")) {
                if (sql.startsWith("INSERT INTO resource_spawn") && attempts.incrementAndGet()==1)
                    throw new DataAccessResourceFailureException("simulated insert failure");
                return 1;
            }
            throw new AssertionError("Unexpected JDBC operation: "+sql);
        });
        var member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.APROVADO);
        when(member.isProfileComplete()).thenReturn(true);
        var members = mock(MemberRepository.class);
        when(members.findByGameCharacter("RetryProbe")).thenReturn(List.of(member));
        var service = new ResourceSpawnService(jdbc,members,mock(AuditLogService.class),mock(WebSocketPublisher.class));
        String body = "S\t123\t10\t20\t"+name+"\n";
        assertThrows(DataAccessResourceFailureException.class,()->service.game("RetryProbe",map,0,body));
        assertEquals("A\t0\n",service.game("RetryProbe",map,0,body));
        assertEquals(2,attempts.get(),"Failed insert must be attempted again without granting visibility");
    }
    public static void main(String[] args) {
        new ResourceRetryTest().failedInsertDoesNotSuppressRetry(2,"Heart of Caves");
        new ResourceRetryTest().failedInsertDoesNotSuppressRetry(10,"Heart of Ice");
        new ResourceRetryTest().failedInsertDoesNotSuppressRetry(11,"Heart of Sand");
        System.out.println("PASS JUnit regression cases for all three T3 names");
    }
}
