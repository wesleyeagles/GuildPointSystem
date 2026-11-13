package com.guild.app.item.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.item.entity.Item;
import com.guild.app.item.repository.ItemRepository;
import com.guild.app.member.entity.Member;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionItemLookupTest {
    @Mock ItemRepository items;
    @InjectMocks ItemService service;
    void login(Role role) {
        var member = new Member(); member.setRole(role); var principal = new MemberPrincipal(member);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @BeforeEach void admin() { login(Role.ADMINISTRADOR); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void returnsOnlyUniqueActiveMatch() {
        var item = new Item(); item.setId(17L); item.setName("Test staff");
        when(items.findTop2ByGameCodeIgnoreCaseAndDeletedFalse("iwtji40")).thenReturn(List.of(item));
        assertEquals(new ItemService.AuctionItemRef(17L, "Test staff"), service.getAuctionItem("iwtji40"));
    }
    @Test void missingAndAmbiguousCodesAreNotGuessed() {
        when(items.findTop2ByGameCodeIgnoreCaseAndDeletedFalse("iwtji40")).thenReturn(List.of(), List.of(new Item(), new Item()));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(AppException.class, () -> service.getAuctionItem("iwtji40")).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(AppException.class, () -> service.getAuctionItem("iwtji40")).getStatus());
    }
    @Test void resolvesInventoryCodeToTheExistingFreakVaferNifferRelic() {
        var relic = new Item();
        relic.setId(44L);
        relic.setName("(1GR) Freak Vafer Niffer Relic");
        when(items.findTop2ByGameCodeIgnoreCaseAndDeletedFalse("iyfig89")).thenReturn(List.of(), List.of(relic));
        when(items.findTop2ByGameCodeIgnoreCaseAndDeletedFalse("relic004")).thenReturn(List.of(relic));

        assertEquals(new ItemService.AuctionItemRef(44L, relic.getName()), service.getAuctionItem("iyfig89"));
        assertEquals(new ItemService.AuctionItemRef(44L, relic.getName()), service.getAuctionItem("iyfig89"));
        verify(items, times(1)).findTop2ByGameCodeIgnoreCaseAndDeletedFalse("relic004");
    }

    @Test void mapsTradableRelicsAcrossAllSixGrades() {
        assertEquals("relic004", AuctionRelicCodes.catalogCode("IYFIG89"));
        assertEquals("relic014", AuctionRelicCodes.catalogCode("iyfig99"));
        assertEquals("relic032", AuctionRelicCodes.catalogCode("iyfip18"));
        assertEquals("relic078", AuctionRelicCodes.catalogCode("iyfis43"));
        assertEquals("relic051", AuctionRelicCodes.catalogCode("iyfip37"));
        assertEquals("relic119", AuctionRelicCodes.catalogCode("iyfix52"));
        assertNull(AuctionRelicCodes.catalogCode("ipevn45"));
    }
    @Test void validatesCodeAndAdminPermission() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(AppException.class, () -> service.getAuctionItem("../bad")).getStatus());
        login(Role.MEMBRO);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(AppException.class, () -> service.getAuctionItem("iwtji40")).getStatus());
        verifyNoInteractions(items);
    }
}
