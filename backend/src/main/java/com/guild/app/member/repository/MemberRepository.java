package com.guild.app.member.repository;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    List<Member> findByNicknameIgnoreCaseAndStatusNotOrderById(String nickname, MemberStatus status);

    /**
     * Contas que usam este personagem do jogo (nickname sem diferenciar caixa). O nick nao e unico no
     * site: quem compartilha o personagem tem uma conta cada, e todas recebem o que a DLL envia.
     * Contas excluidas mantem o nick e ficam de fora.
     */
    default List<Member> findByGameCharacter(String character) {
        return findByNicknameIgnoreCaseAndStatusNotOrderById(character, MemberStatus.EXCLUIDO);
    }

    Optional<Member> findByDiscordId(String discordId);

    List<Member> findByDiscordIdIn(Collection<String> discordIds);

    boolean existsByEmail(String email);

    boolean existsByDiscordId(String discordId);

    Optional<Member> findByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForUpdate(Long id);

    List<Member> findByStatusOrderByPointsDesc(com.guild.app.common.enums.MemberStatus status);

    // Same effective character level as the equipment board, including the latest snapshot tie-break.
    @Query(value = """
        SELECT m.* FROM member m
        LEFT JOIN LATERAL (SELECT level FROM character_status WHERE member_id=m.id
            ORDER BY updated_at DESC,id DESC LIMIT 1) cs ON true
        WHERE m.status='APROVADO' AND COALESCE(cs.level,m.level)=50
        """, nativeQuery = true)
    List<Member> findApprovedLevel50ForBd();

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.characterClass WHERE m.id IN :ids")
    List<Member> findAllByIdInWithClass(Collection<Long> ids);
}
