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
    @Test void validatesCodeAndAdminPermission() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(AppException.class, () -> service.getAuctionItem("../bad")).getStatus());
        login(Role.MEMBRO);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(AppException.class, () -> service.getAuctionItem("iwtji40")).getStatus());
        verifyNoInteractions(items);
    }
}
