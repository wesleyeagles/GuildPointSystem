package com.guild.app.rotation.service;

import com.guild.app.common.enums.*;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.rotation.dto.RotationDtos.*;
import com.guild.app.rotation.repository.RotationRepository;
import com.guild.app.schedule.GameEventDefinitions.RotationArea;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class RotationService {
    private final RotationRepository repository;
    private final MemberRepository members;
    private final AuditLogService audit;
    private final WebSocketPublisher publisher;
    private Member actor() {
        var member=members.findById(SecurityUtils.currentMember().getId()).orElseThrow();
        if(member.getStatus()!=MemberStatus.APROVADO || !member.isProfileComplete() || member.getDeletedAt()!=null)
            throw new AppException("Acesso não permitido.",HttpStatus.FORBIDDEN);
        return member;
    }
    public Schedule read() { actor(); return repository.read(); }

    /** Nossa guilda na rotação: é dela o painel de PBs do site e do jogo. */
    public static final Guild HOME_GUILD=Guild.Blacklist;

    /**
     * Horas cheias (Brasília) em que a guilda tem boss em cada área. Base dos contadores e alertas
     * de PB HQ/Colônia, que pulam os horários sem PB nosso. Leitura interna, sem membro logado.
     */
    public Map<RotationArea,Set<Integer>> pbHours(Guild guild) {
        var hours=new EnumMap<RotationArea,Set<Integer>>(RotationArea.class);
        for(var area:RotationArea.values()) hours.put(area,new TreeSet<>());
        for(var slot:repository.read().slots()) {
            if(!slot.time().endsWith(":00")) continue;
            int hour=Integer.parseInt(slot.time().substring(0,2));
            if(has(slot.qg(),guild)) hours.get(RotationArea.QG).add(hour);
            if(has(slot.outpost(),guild)) hours.get(RotationArea.OUTPOST).add(hour);
        }
        return hours;
    }
    private static boolean has(List<Assignment> area,Guild guild) {
        return area.stream().anyMatch(a->a.guild()==guild && !a.bosses().isEmpty());
    }

    /**
     * Rotação da nossa guilda para a DLL do jogo, ligada pelo nome do personagem como os outros
     * envios da DLL (sem login): só responde a personagem de membro aprovado. Uma linha por horário
     * com PB nosso: "HH:MM<TAB>bosses do QG<TAB>bosses do outpost", bosses separados por " / ".
     */
    public Optional<String> gameText(String character) {
        if(character==null || character.isBlank() || character.length()>32) return Optional.empty();
        boolean member=members.findByGameCharacter(character.strip()).stream().anyMatch(m->
            m.getStatus()==MemberStatus.APROVADO && m.isProfileComplete() && m.getDeletedAt()==null && m.getSuspendedAt()==null);
        if(!member) return Optional.empty();
        var text=new StringBuilder();
        for(var slot:repository.read().slots()) {
            var qg=bosses(slot.qg(),HOME_GUILD); var outpost=bosses(slot.outpost(),HOME_GUILD);
            if(qg.isEmpty() && outpost.isEmpty()) continue;
            text.append(slot.time()).append('\t').append(qg).append('\t').append(outpost).append('\n');
        }
        return Optional.of(text.toString());
    }
    private static String bosses(List<Assignment> area,Guild guild) {
        // Tab e quebra de linha separam os campos: nunca vão dentro de um nome.
        return area.stream().filter(a->a.guild()==guild).flatMap(a->a.bosses().stream())
            .map(b->b.replaceAll("\\p{Cntrl}"," ").strip()).filter(b->!b.isEmpty())
            .collect(java.util.stream.Collectors.joining(" / "));
    }
    @Transactional public Schedule update(Update request) {
        var actor=actor();
        if(!actor.getRole().isAtLeast(Role.ADMINISTRADOR)) throw new AppException("Apenas administradores podem editar a rotação.",HttpStatus.FORBIDDEN);
        var times=new HashSet<String>();
        for(var slot:request.slots()) {
            if(!times.add(slot.time())) throw new AppException("Não repita horários.",HttpStatus.BAD_REQUEST);
            for(var assignments:List.of(slot.qg(),slot.outpost())) {
                var guilds=new HashSet<Guild>(); var bosses=new HashSet<String>();
                for(var entry:assignments) {
                    if(!guilds.add(entry.guild())) throw new AppException("Agrupe os bosses da mesma guilda no horário e área.",HttpStatus.BAD_REQUEST);
                    for(var boss:entry.bosses()) if(!bosses.add(boss.strip().toLowerCase(Locale.ROOT)))
                        throw new AppException("Um boss não pode ter duas atribuições no mesmo horário e área.",HttpStatus.BAD_REQUEST);
                }
            }
        }
        var sorted=new Update(request.version(),request.slots().stream().sorted(Comparator.comparing(Slot::time)).toList());
        if(!repository.update(sorted,actor.getId())) throw new AppException("A rotação foi atualizada por outra pessoa. Recarregue antes de salvar.",HttpStatus.CONFLICT);
        audit.log(AuditLogType.ROTATION_UPDATED,actor,null,Map.of("version",request.version()+1,"slots",request.slots().size()));
        publisher.publishRotationChanged();
        return repository.read();
    }
}
