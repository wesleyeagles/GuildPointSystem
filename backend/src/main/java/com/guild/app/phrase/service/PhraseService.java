package com.guild.app.phrase.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.phrase.dto.PhraseBoardResponse;
import com.guild.app.phrase.dto.PhraseRequest;
import com.guild.app.phrase.dto.PhraseResponse;
import com.guild.app.phrase.entity.Phrase;
import com.guild.app.phrase.entity.PhraseVote;
import com.guild.app.phrase.repository.PhraseRepository;
import com.guild.app.phrase.repository.PhraseVoteRepository;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PhraseService {

    /** Quantas frases cada membro pode votar ao mesmo tempo. */
    public static final int MAX_VOTES = 3;

    private final PhraseRepository phraseRepository;
    private final PhraseVoteRepository voteRepository;
    private final MemberRepository memberRepository;
    private final WebSocketPublisher webSocketPublisher;

    @Transactional(readOnly = true)
    public PhraseBoardResponse board(MemberPrincipal actor) {
        Map<Long, List<String>> voters = new HashMap<>();
        List<Long> myVotes = new ArrayList<>();
        for (Object[] row : voteRepository.findAllWithNickname()) {
            var phraseId = (Long) row[0];
            voters.computeIfAbsent(phraseId, k -> new ArrayList<>()).add((String) row[2]);
            if (Objects.equals(row[1], actor.getId())) myVotes.add(phraseId);
        }
        var phrases = phraseRepository.findAllByOrderByIdAsc().stream()
                .map(p -> toResponse(p, voters.getOrDefault(p.getId(), List.of())))
                .toList();
        return new PhraseBoardResponse(MAX_VOTES, myVotes, phrases);
    }

    /** Vota numa frase. Votar de novo na mesma não muda nada; passar do limite é recusado. */
    @Transactional
    public PhraseBoardResponse vote(Long phraseId, MemberPrincipal actor) {
        requirePhrase(phraseId);
        // Trava o membro: dois cliques ao mesmo tempo não passam do limite.
        memberRepository.findByIdForUpdate(actor.getId())
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));
        if (!voteRepository.existsByPhraseIdAndMemberId(phraseId, actor.getId())) {
            if (voteRepository.countByMemberId(actor.getId()) >= MAX_VOTES) {
                throw new AppException("Você já usou seus " + MAX_VOTES
                        + " votos. Tire um voto para votar em outra frase.", HttpStatus.BAD_REQUEST);
            }
            voteRepository.saveAndFlush(new PhraseVote(phraseId, actor.getId()));
            webSocketPublisher.publishPhrasesChanged();
        }
        return board(actor);
    }

    @Transactional
    public PhraseBoardResponse unvote(Long phraseId, MemberPrincipal actor) {
        if (voteRepository.deleteByPhraseIdAndMemberId(phraseId, actor.getId()) > 0) {
            webSocketPublisher.publishPhrasesChanged();
        }
        return board(actor);
    }

    @Transactional
    public PhraseResponse create(PhraseRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var phrase = new Phrase();
        apply(phrase, request);
        if (phrase.getSaidAt() == null) phrase.setSaidAt(Instant.now());
        phrase.setCreatedBy(memberRepository.getReferenceById(actor.getId()));
        phrase = phraseRepository.save(phrase);
        webSocketPublisher.publishPhrasesChanged();
        return toResponse(phrase, List.of());
    }

    @Transactional
    public PhraseResponse update(Long id, PhraseRequest request) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var phrase = requirePhrase(id);
        apply(phrase, request);
        phrase.setUpdatedAt(Instant.now());
        phraseRepository.save(phrase);
        webSocketPublisher.publishPhrasesChanged();
        return toResponse(phrase, List.of());
    }

    @Transactional
    public void delete(Long id) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        phraseRepository.delete(requirePhrase(id)); // os votos saem junto (ON DELETE CASCADE no banco)
        webSocketPublisher.publishPhrasesChanged();
    }

    private void apply(Phrase phrase, PhraseRequest request) {
        Member member = memberRepository.findById(request.memberId())
                .filter(m -> m.getStatus() != MemberStatus.EXCLUIDO)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.BAD_REQUEST));
        phrase.setText(request.text().trim());
        phrase.setMember(member);
        phrase.setSaidBy(member.getNickname());
        if (request.saidAt() != null) phrase.setSaidAt(request.saidAt());
    }

    private Phrase requirePhrase(Long id) {
        return phraseRepository.findById(id)
                .orElseThrow(() -> new AppException("Frase não encontrada.", HttpStatus.NOT_FOUND));
    }

    private PhraseResponse toResponse(Phrase phrase, List<String> voters) {
        var member = phrase.getMember();
        return new PhraseResponse(
                phrase.getId(),
                phrase.getText(),
                member != null ? member.getId() : null,
                member != null ? member.getNickname() : null,
                member != null ? member.getAvatarUrl() : null,
                member != null ? member.getNickname() : phrase.getSaidBy(),
                phrase.getSaidAt(),
                voters.size(),
                voters);
    }
}
