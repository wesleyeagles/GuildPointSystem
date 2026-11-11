package com.guild.app.market;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.market.service.MarketPage;
import com.guild.app.market.service.MarketPage.Listing;
import com.guild.app.market.service.MarketPriceService;
import com.guild.app.market.service.MarketSource;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketPriceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired MarketPriceService service;
    @MockitoBean WebSocketPublisher ws;
    @MockitoBean MarketSource source;
    @Autowired MemberRepository members;
    String approved, pending, unlisted;
    long approvedId, leaderId, unlistedId;

    /** Trecho do HTML real do market (11/11/2026), com dois anúncios. */
    static final String PAGE = """
        <!DOCTYPE html><html id="XF" data-logged-in="true" data-csrf="1,abc"><body>
        <div class="itemInfo"><div class="itemBlock">
          <div onclick="itemDetailsHandler(this, 138443)">
            <div class="itemName grade2"> Vampire Big Sword Breaker </div>
            <div class="itemLevel"><div class="level"> 28 </div><div class="upgradeLevel"> +3 </div></div>
          </div>
          <div style="display: flex">
            <div class="itemAmount" onclick="itemDetailsHandler(this, 138443)"> 1 pcs. </div>
            <div class="itemBuy"><a href="/market/?like=iwshi28" class="similarItem"><button>&#xf002;</button></a>
              <div class="buy blocked">1 499 CS</div></div>
          </div></div>
          <div class="itemDetails" id="details138443"><div class="itemName grade2">Vampire Big Sword Breaker</div></div>
        </div>
        <div class="itemInfo"><div class="itemBlock">
          <div class="itemName grade0"> Cactus Seeds </div>
          <div class="itemAmount"> 4 pcs. </div>
          <div class="itemBuy"><a href="/market/?like=ibxyz01" class="similarItem"></a><div class="buy">10 CS</div></div>
        </div></div>
        <div class="Pagination"><div class="pageBtn active">1</div><div class="pageBtn ">2</div><div class="pageBtn">7</div></div>
        </body></html>""";

    long member(String nick, String status) { return member(nick, status, "MEMBRO"); }
    long member(String nick, String status, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    @BeforeEach void setup() {
        approved = nick("Mkt");
        approvedId = member(approved, "APROVADO");
        pending = nick("Pen");
        unlisted = nick("Out");
        unlistedId = member(unlisted, "APROVADO");
        leaderId = jdbc.queryForObject("SELECT id FROM member WHERE role='LIDER' ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO market_price_user(member_id) VALUES(?)", approvedId);
        jdbc.update("INSERT INTO market_price_user(member_id) VALUES(?)", member(pending, "PENDENTE"));
    }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }
    @AfterEach void forget() { service.forgetCache(); }

    ResultActions price(String character, String code) throws Exception {
        return mvc.perform(get("/api/market/game/price").param("character", character).param("code", code));
    }
    String ok(String code) throws Exception {
        return price(approved, code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    static Listing l(String code, int upgrade, int quantity, long price) { return new Listing(code, "", upgrade, quantity, price); }

    @Test void parsesRealMarkup() {
        MarketPage page = MarketPage.parse(PAGE);
        assertTrue(page.loggedIn());
        assertEquals(List.of(new Listing("iwshi28", "Vampire Big Sword Breaker", 3, 1, 1499),
                             new Listing("ibxyz01", "Cactus Seeds", 0, 4, 10)), page.listings());
        assertFalse(MarketPage.parse("<html data-logged-in=\"false\"><body>login</body></html>").loggedIn());
    }

    @Test void minAndAverageByUpgradePerUnit() throws Exception {
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(
            l("iwshi28", 0, 1, 15), l("iwshi28", 0, 1, 25), l("iwshi28", 0, 2, 30),   // 15, 25, 15 por unidade
            l("iwshi28", 3, 1, 1499), l("iwshi28", 3, 1, 1500),
            l("iwshi29", 0, 1, 1))));                                                // outro código: ignorado
        assertEquals("0\n0\t15\t18.33\t3\n3\t1499\t1499.5\t2\n", ok("IWSHI28"));
    }

    @Test void cachesSoOneMarketReadServesEveryone() throws Exception {
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(l("iwshi28", 0, 1, 15))));
        ok("iwshi28");
        ok("iwshi28");
        verify(source, times(1)).listings("iwshi28");
    }

    @Test void nobodySellingIsJustTheAgeLine() throws Exception {
        when(source.listings("iwzzz99")).thenReturn(Optional.of(List.of()));
        assertEquals("0\n", ok("iwzzz99"));
    }

    @Test void marketDownWithoutCacheIs503() throws Exception {
        when(source.listings("iwshi28")).thenReturn(Optional.empty());
        price(approved, "iwshi28").andExpect(status().isServiceUnavailable());
    }

    @Test void onlyApprovedMembersAndValidCodes() throws Exception {
        price(pending, "iwshi28").andExpect(status().isForbidden());
        price(unlisted, "iwshi28").andExpect(status().isForbidden());           // aprovado, mas fora da lista
        price("NinguemAqui", "iwshi28").andExpect(status().isForbidden());
        price(approved, "../etc").andExpect(status().isBadRequest());
        verifyNoInteractions(source);
    }

    @Test void gameAccessOnlyForTheList() throws Exception {
        mvc.perform(get("/api/market/game/access").param("character", approved)).andExpect(status().isNoContent());
        mvc.perform(get("/api/market/game/access").param("character", unlisted)).andExpect(status().isNotFound());
        mvc.perform(get("/api/market/game/access").param("character", pending)).andExpect(status().isNotFound());
    }

    @Test void onlyTheLeaderEditsTheList() throws Exception {
        assertFalse(service.view(as(approvedId)).canConfigure());
        assertThrows(AppException.class, () -> service.update(List.of(unlistedId), as(approvedId)));
        var view = service.update(List.of(unlistedId), as(leaderId));
        assertEquals(List.of(unlisted), view.viewers().stream().map(MarketPriceService.Viewer::nickname).toList());
        mvc.perform(get("/api/market/game/access").param("character", unlisted)).andExpect(status().isNoContent());
        mvc.perform(get("/api/market/game/access").param("character", approved)).andExpect(status().isNotFound());
    }
}
