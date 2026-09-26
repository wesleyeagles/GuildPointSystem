package com.guild.app.party.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.PartyMap;
import com.guild.app.common.enums.PartyPendingKind;
import com.guild.app.common.enums.PartyPendingStatus;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.party.dto.*;
import com.guild.app.party.entity.*;
import com.guild.app.party.repository.*;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyPendingRepository partyPendingRepository;
    private final PartyLfgRepository partyLfgRepository;
    private final MemberRepository memberRepository;
    private final WebSocketPublisher webSocketPublisher;

    @Transactional(readOnly = true)
    public PartyBoardResponse getBoard(PartyMap map, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var parties = partyRepository.findByMapOrderByCreatedAtAsc(map);
        var partyIds = parties.stream().map(Party::getId).toList();

        Map<Long, List<PartyMember>> membersByParty = new HashMap<>();
        Set<Long> memberIds = new HashSet<>();
        memberIds.add(member.getId());

        if (!partyIds.isEmpty()) {
            var partyMembers = partyMemberRepository.findByPartyIdInWithMember(partyIds);
            partyMembers.forEach(pm -> {
                membersByParty.computeIfAbsent(pm.getPartyId(), k -> new ArrayList<>()).add(pm);
                memberIds.add(pm.getMemberId());
            });
            membersByParty.values().forEach(list ->
                    list.sort(Comparator.comparing(PartyMember::getJoinedAt)));
        }

        var lfgRows = partyLfgRepository.findByMapOrderByCreatedAtAsc(map);
        lfgRows.forEach(l -> memberIds.add(l.getMemberId()));

        final Map<Long, Member> memberLookup = memberIds.isEmpty()
                ? Map.of()
                : memberRepository.findAllByIdInWithClass(memberIds).stream()
                        .collect(Collectors.toMap(Member::getId, Function.identity()));

        var partySummaries = parties.stream()
                .map(p -> toPartySummary(p, membersByParty.getOrDefault(p.getId(), List.of()), memberLookup))
                .toList();

        var lfg = lfgRows.stream()
                .map(row -> toLfgEntry(row, memberLookup))
                .toList();

        var my = buildMyState(member, memberLookup);
        return new PartyBoardResponse(map, partySummaries, lfg, my);
    }

    @Transactional
    public PartyBoardResponse createParty(CreatePartyRequest request, MemberPrincipal actor) {
        var member = requireApproved(actor);
        ensureNotInParty(member.getId());

        var party = new Party();
        party.setMap(request.map());
        party.setLeader(member);
        party = partyRepository.save(party);

        addMemberRow(party.getId(), member.getId());
        partyLfgRepository.findByMemberId(member.getId()).ifPresent(partyLfgRepository::delete);

        publishBoard(request.map());
        return getBoard(request.map(), actor);
    }

    @Transactional
    public PartyBoardResponse disband(Long partyId, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var party = partyRepository.findById(partyId)
                .orElseThrow(() -> new AppException("PT não encontrada", HttpStatus.NOT_FOUND));
        if (!party.getLeader().getId().equals(member.getId())) {
            throw new AppException("Apenas o líder pode dissolver a PT", HttpStatus.FORBIDDEN);
        }
        var map = party.getMap();
        partyRepository.delete(party);
        publishBoard(map);
        return getBoard(map, actor);
    }

    @Transactional
    public PartyBoardResponse leave(Long partyId, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var party = partyRepository.findById(partyId)
                .orElseThrow(() -> new AppException("PT não encontrada", HttpStatus.NOT_FOUND));
        var map = party.getMap();

        if (!partyMemberRepository.findByMemberId(member.getId())
                .map(pm -> pm.getPartyId().equals(partyId))
                .orElse(false)) {
            throw new AppException("Você não está nesta PT", HttpStatus.BAD_REQUEST);
        }

        boolean wasLeader = party.getLeader().getId().equals(member.getId());
        partyMemberRepository.deleteByPartyIdAndMemberId(partyId, member.getId());

        if (partyMemberRepository.countByPartyId(partyId) == 0) {
            partyRepository.delete(party);
        } else if (wasLeader) {
            var nextMemberId = partyMemberRepository.findByPartyIdOrderByJoinedAtAsc(partyId).getFirst().getMemberId();
            party.setLeader(memberRepository.findById(nextMemberId).orElseThrow());
            partyRepository.save(party);
        }

        cancelPendingInvolvingMemberInParty(partyId, member.getId());
        publishBoard(map);
        return getBoard(map, actor);
    }

    @Transactional
    public PartyBoardResponse requestJoin(Long partyId, MemberPrincipal actor) {
        var member = requireApproved(actor);
        ensureNotInParty(member.getId());

        var party = partyRepository.findById(partyId)
                .orElseThrow(() -> new AppException("PT não encontrada", HttpStatus.NOT_FOUND));
        if (partyMemberRepository.countByPartyId(partyId) >= Party.MAX_MEMBERS) {
            throw new AppException("PT cheia", HttpStatus.BAD_REQUEST);
        }

        partyPendingRepository.findByPartyIdAndInitiatorIdAndKindAndStatus(
                partyId, member.getId(), PartyPendingKind.JOIN_REQUEST, PartyPendingStatus.PENDING
        ).ifPresent(p -> {
            throw new AppException("Você já tem um pedido pendente para esta PT", HttpStatus.CONFLICT);
        });

        var pending = new PartyPending();
        pending.setParty(party);
        pending.setKind(PartyPendingKind.JOIN_REQUEST);
        pending.setInitiator(member);
        pending.setTarget(party.getLeader());
        pending.setStatus(PartyPendingStatus.PENDING);
        partyPendingRepository.save(pending);

        publishBoard(party.getMap());
        return getBoard(party.getMap(), actor);
    }

    @Transactional
    public PartyBoardResponse invite(Long partyId, InviteMemberRequest request, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var party = partyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new AppException("PT não encontrada", HttpStatus.NOT_FOUND));
        if (!party.getLeader().getId().equals(member.getId())) {
            throw new AppException("Apenas o líder pode convidar", HttpStatus.FORBIDDEN);
        }
        if (partyMemberRepository.countByPartyId(partyId) >= Party.MAX_MEMBERS) {
            throw new AppException("PT cheia", HttpStatus.BAD_REQUEST);
        }

        var target = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (target.getStatus() != MemberStatus.APROVADO) {
            throw new AppException("Membro não disponível", HttpStatus.BAD_REQUEST);
        }
        if (partyMemberRepository.findByMemberId(target.getId()).isPresent()) {
            throw new AppException("Membro já está em uma PT", HttpStatus.BAD_REQUEST);
        }
        if (target.getId().equals(member.getId())) {
            throw new AppException("Não é possível convidar a si mesmo", HttpStatus.BAD_REQUEST);
        }

        partyPendingRepository.findByPartyIdAndTargetIdAndStatus(
                partyId, target.getId(), PartyPendingStatus.PENDING
        ).ifPresent(p -> {
            throw new AppException("Já existe convite ou pedido pendente para este membro", HttpStatus.CONFLICT);
        });

        var pending = new PartyPending();
        pending.setParty(party);
        pending.setKind(PartyPendingKind.INVITE);
        pending.setInitiator(member);
        pending.setTarget(target);
        partyPendingRepository.save(pending);

        publishBoard(party.getMap());
        return getBoard(party.getMap(), actor);
    }

    @Transactional
    public PartyBoardResponse acceptPending(Long pendingId, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var pending = partyPendingRepository.findById(pendingId)
                .orElseThrow(() -> new AppException("Solicitação não encontrada", HttpStatus.NOT_FOUND));
        if (pending.getStatus() != PartyPendingStatus.PENDING) {
            throw new AppException("Solicitação já foi resolvida", HttpStatus.BAD_REQUEST);
        }

        var party = partyRepository.findByIdForUpdate(pending.getParty().getId())
                .orElseThrow(() -> new AppException("PT não encontrada", HttpStatus.NOT_FOUND));
        var map = party.getMap();

        if (pending.getKind() == PartyPendingKind.JOIN_REQUEST) {
            if (!party.getLeader().getId().equals(member.getId())) {
                throw new AppException("Apenas o líder pode aceitar pedidos de entrada", HttpStatus.FORBIDDEN);
            }
            addMemberLocked(party, pending.getInitiator());
            pending.setStatus(PartyPendingStatus.ACCEPTED);
        } else {
            if (!pending.getTarget().getId().equals(member.getId())) {
                throw new AppException("Apenas o convidado pode aceitar o convite", HttpStatus.FORBIDDEN);
            }
            addMemberLocked(party, member);
            pending.setStatus(PartyPendingStatus.ACCEPTED);
        }
        partyPendingRepository.save(pending);
        long joinedMemberId = pending.getKind() == PartyPendingKind.JOIN_REQUEST
                ? pending.getInitiator().getId()
                : member.getId();
        cancelOtherPendingForMember(joinedMemberId);
        rejectRemainingJoinRequestsIfFull(party.getId());

        publishBoard(map);
        return getBoard(map, actor);
    }

    @Transactional
    public PartyBoardResponse rejectPending(Long pendingId, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var pending = partyPendingRepository.findById(pendingId)
                .orElseThrow(() -> new AppException("Solicitação não encontrada", HttpStatus.NOT_FOUND));
        if (pending.getStatus() != PartyPendingStatus.PENDING) {
            throw new AppException("Solicitação já foi resolvida", HttpStatus.BAD_REQUEST);
        }

        assertCanResolvePending(pending, member);
        pending.setStatus(PartyPendingStatus.REJECTED);
        partyPendingRepository.save(pending);

        var map = pending.getParty().getMap();
        publishBoard(map);
        return getBoard(map, actor);
    }

    @Transactional
    public PartyBoardResponse cancelPending(Long pendingId, MemberPrincipal actor) {
        var member = requireApproved(actor);
        var pending = partyPendingRepository.findById(pendingId)
                .orElseThrow(() -> new AppException("Solicitação não encontrada", HttpStatus.NOT_FOUND));
        if (pending.getStatus() != PartyPendingStatus.PENDING) {
            throw new AppException("Solicitação já foi resolvida", HttpStatus.BAD_REQUEST);
        }
        if (!pending.getInitiator().getId().equals(member.getId())) {
            throw new AppException("Apenas quem iniciou pode cancelar", HttpStatus.FORBIDDEN);
        }

        pending.setStatus(PartyPendingStatus.CANCELLED);
        partyPendingRepository.save(pending);

        var map = pending.getParty().getMap();
        publishBoard(map);
        return getBoard(map, actor);
    }

    @Transactional
    public PartyBoardResponse upsertLfg(UpsertLfgRequest request, MemberPrincipal actor) {
        var member = requireApproved(actor);
        if (partyMemberRepository.findByMemberId(member.getId()).isPresent()) {
            throw new AppException("Saia da PT antes de entrar na lista de espera", HttpStatus.BAD_REQUEST);
        }

        var existing = partyLfgRepository.findByMemberId(member.getId());
        PartyMap previousMap = existing.map(PartyLfg::getMap).orElse(null);

        PartyLfg entry;
        if (existing.isPresent()) {
            entry = existing.get();
        } else {
            entry = new PartyLfg();
            entry.setMemberId(member.getId());
            entry.setCreatedAt(Instant.now());
        }
        entry.setMap(request.map());
        entry.setNote(request.note() != null && !request.note().isBlank() ? request.note().trim() : null);
        partyLfgRepository.save(entry);

        publishBoard(request.map());
        if (previousMap != null && previousMap != request.map()) {
            publishBoard(previousMap);
        }
        return getBoard(request.map(), actor);
    }

    @Transactional
    public PartyBoardResponse leaveLfg(MemberPrincipal actor, PartyMap viewMap) {
        var member = requireApproved(actor);
        var existing = partyLfgRepository.findByMemberId(member.getId());
        if (existing.isEmpty()) {
            throw new AppException("Você não está na lista de espera", HttpStatus.BAD_REQUEST);
        }
        var map = existing.get().getMap();
        partyLfgRepository.delete(existing.get());
        publishBoard(map);
        return getBoard(viewMap, actor);
    }

    private void addMemberLocked(Party party, Member newMember) {
        if (partyMemberRepository.findByMemberId(newMember.getId()).isPresent()) {
            throw new AppException("Membro já está em uma PT", HttpStatus.BAD_REQUEST);
        }
        if (partyMemberRepository.countByPartyId(party.getId()) >= Party.MAX_MEMBERS) {
            throw new AppException("PT cheia", HttpStatus.BAD_REQUEST);
        }
        addMemberRow(party.getId(), newMember.getId());
        partyLfgRepository.findByMemberId(newMember.getId()).ifPresent(partyLfgRepository::delete);
    }

    private void addMemberRow(Long partyId, Long memberId) {
        var row = new PartyMember();
        row.setPartyId(partyId);
        row.setMemberId(memberId);
        row.setJoinedAt(Instant.now());
        partyMemberRepository.save(row);
    }

    private void cancelOtherPendingForMember(Long memberId) {
        for (var p : partyPendingRepository.findActiveForMember(memberId, PartyPendingStatus.PENDING)) {
            p.setStatus(PartyPendingStatus.CANCELLED);
            partyPendingRepository.save(p);
        }
    }

    private void rejectRemainingJoinRequestsIfFull(Long partyId) {
        if (partyMemberRepository.countByPartyId(partyId) < Party.MAX_MEMBERS) {
            return;
        }
        partyPendingRepository.findByPartyIdAndStatus(partyId, PartyPendingStatus.PENDING).forEach(p -> {
            if (p.getKind() == PartyPendingKind.JOIN_REQUEST) {
                p.setStatus(PartyPendingStatus.REJECTED);
                partyPendingRepository.save(p);
            }
        });
    }

    private void cancelPendingInvolvingMemberInParty(Long partyId, Long memberId) {
        partyPendingRepository.findByPartyIdAndStatus(partyId, PartyPendingStatus.PENDING).forEach(p -> {
            if (p.getInitiator().getId().equals(memberId) || p.getTarget().getId().equals(memberId)) {
                p.setStatus(PartyPendingStatus.CANCELLED);
                partyPendingRepository.save(p);
            }
        });
    }

    private void assertCanResolvePending(PartyPending pending, Member actor) {
        if (pending.getKind() == PartyPendingKind.JOIN_REQUEST) {
            if (!pending.getParty().getLeader().getId().equals(actor.getId())) {
                throw new AppException("Apenas o líder pode responder pedidos de entrada", HttpStatus.FORBIDDEN);
            }
        } else if (!pending.getTarget().getId().equals(actor.getId())) {
            throw new AppException("Apenas o convidado pode responder ao convite", HttpStatus.FORBIDDEN);
        }
    }

    private MyPartyState buildMyState(Member member, Map<Long, Member> memberLookup) {
        var membership = partyMemberRepository.findByMemberId(member.getId());
        Long partyId = null;
        PartyMap partyMap = null;
        boolean leader = false;

        if (membership.isPresent()) {
            var party = partyRepository.findById(membership.get().getPartyId()).orElseThrow();
            partyId = party.getId();
            partyMap = party.getMap();
            leader = party.getLeader().getId().equals(member.getId());
        }

        PartyLfgEntry lfgEntry = partyLfgRepository.findByMemberId(member.getId())
                .map(lfg -> toLfgEntry(lfg, memberLookup))
                .orElse(null);

        var pending = partyPendingRepository.findActiveForMember(member.getId(), PartyPendingStatus.PENDING)
                .stream()
                .map(p -> toPendingResponse(p, member.getId()))
                .toList();

        return new MyPartyState(partyId, partyMap, leader, lfgEntry, pending);
    }

    private PartyPendingResponse toPendingResponse(PartyPending p, Long viewerId) {
        Member other = p.getInitiator().getId().equals(viewerId) ? p.getTarget() : p.getInitiator();
        return new PartyPendingResponse(
                p.getId(),
                p.getKind(),
                p.getParty().getId(),
                p.getParty().getMap(),
                toMemberSummary(other, p.getParty().getLeader().getId().equals(other.getId())),
                p.getCreatedAt()
        );
    }

    private PartySummary toPartySummary(Party party, List<PartyMember> members, Map<Long, Member> memberLookup) {
        Long leaderId = party.getLeader().getId();
        var summaries = members.stream()
                .map(pm -> {
                    Member m = pm.getMember();
                    if (m == null) {
                        m = memberLookup.get(pm.getMemberId());
                    }
                    if (m == null) {
                        throw new AppException("Membro da PT não encontrado", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    return toMemberSummary(m, pm.getMemberId().equals(leaderId));
                })
                .toList();
        return new PartySummary(party.getId(), party.getMap(), leaderId, summaries, summaries.size());
    }

    private PartyMemberSummary toMemberSummary(Member m, boolean leader) {
        var cls = m.getCharacterClass();
        return new PartyMemberSummary(
                m.getId(),
                m.getNickname(),
                cls != null ? cls.getName() : null,
                cls != null ? cls.getImageUrl() : null,
                m.getLevel() != null ? m.getLevel() : 1,
                leader
        );
    }

    private PartyLfgEntry toLfgEntry(PartyLfg lfg, Map<Long, Member> memberLookup) {
        Member m = lfg.getMember();
        if (m == null) {
            m = memberLookup.get(lfg.getMemberId());
        }
        if (m == null) {
            throw new AppException("Membro da lista de espera não encontrado", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        var cls = m.getCharacterClass();
        return new PartyLfgEntry(
                m.getId(),
                m.getNickname(),
                cls != null ? cls.getName() : null,
                cls != null ? cls.getImageUrl() : null,
                m.getLevel() != null ? m.getLevel() : 1,
                lfg.getMap(),
                lfg.getNote(),
                lfg.getCreatedAt()
        );
    }

    private Member requireApproved(MemberPrincipal actor) {
        var member = memberRepository.findById(actor.getId())
                .orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (member.getStatus() != MemberStatus.APROVADO) {
            throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private void ensureNotInParty(Long memberId) {
        if (partyMemberRepository.findByMemberId(memberId).isPresent()) {
            throw new AppException("Você já está em uma PT", HttpStatus.BAD_REQUEST);
        }
    }

    private void publishBoard(PartyMap map) {
        webSocketPublisher.publishPartyBoard(map);
    }
}
