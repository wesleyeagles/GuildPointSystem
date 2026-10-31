package com.guild.app.quest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.guild.app.quest.dto.QuestDtos.CatalogQuest;
import com.guild.app.quest.dto.QuestDtos.CouponCatalog;
import com.guild.app.quest.dto.QuestDtos.ExperienceCatalog;
import com.guild.app.quest.dto.QuestDtos.DailyQuest;
import com.guild.app.quest.dto.QuestDtos.DailyQuestDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Component
public class QuestCatalog {
    private static final Set<String> DEFAULT_VISIBLE=Set.of("Q071396","Q071397","Q071398","Q071399","Q071400",
        "Q071401","Q071402","Q071403","Q071404","Q071405","Q071382","Q071406","Q071450","Q070944","Q070947");
    public static final String CLIENT="8343dc2afba9537f5c41c713b3af5516d0be75f6ad9a9bdb6ed9722238645379";
    public static final String EDF="fa5e6d236cce98265cae4fc3bc5374ba8560824bbdef94ffb0ed8ec269456e98";
    private final Map<String,CatalogQuest> definitions;
    public final CouponCatalog coupons;
    private final ExperienceCatalog experience;
    public QuestCatalog(ObjectMapper json) throws IOException {
        var map=new LinkedHashMap<String,CatalogQuest>();
        try(var stream=new GZIPInputStream(new ClassPathResource("quest/catalog.json.gz").getInputStream())) {
            var rows=json.readValue(stream,CatalogQuest[].class);
            for(var row:rows) {
                if(!row.definitionHash().matches("[a-f0-9]{64}") || !row.code().matches("Q[0-9A-F]{6}") || map.put(row.definitionHash(),row)!=null)
                    throw new IOException("Invalid quest catalog");
            }

        }
        try(var stream=new GZIPInputStream(new ClassPathResource("quest/coupon-catalog.json.gz").getInputStream())) {
            var legacy=json.readValue(stream,CouponCatalog.class);
            try(var exp=new ClassPathResource("quest/exp-catalog.json").getInputStream()) {
                experience=json.readValue(exp,ExperienceCatalog.class);
            }
            if(experience.quests().size()!=35) throw new IOException("Expected 35 EXP quests");
            for(var row:experience.quests()) {
                if(row.minLevel()==null || row.maxLevel()==null || row.minLevel()>row.maxLevel() ||
                    row.experience()==null || row.experience().signum()<0 || row.rewards()==null ||
                    !row.quest().code().matches("Q[0-9A-F]{6}") || !row.quest().definitionHash().matches("[a-f0-9]{64}"))
                    throw new IOException("Invalid EXP quest");
                var previous=map.putIfAbsent(row.quest().definitionHash(),row.quest());
                if(previous!=null && !previous.equals(row.quest())) throw new IOException("Conflicting EXP definition");
            }
            definitions=Collections.unmodifiableMap(map);
            Map<String,DailyQuestDetails> details;
            try(var streamRewards=new ClassPathResource("quest/daily-details.json").getInputStream()) {
                details=json.readValue(streamRewards,new TypeReference<>() {});
            }
            var quests=new ArrayList<DailyQuest>();
            for(var row:legacy.quests()) {
                var content=details.get(row.quest().code());
                if(content==null || content.objectives()==null || content.objectives().isEmpty()) throw new IOException("Missing daily quest objectives");
                var items=content.rewards();
                if(items==null || items.isEmpty() || items.stream().anyMatch(item->item.quantity()<1 || item.name()==null || item.name().isBlank()))
                    throw new IOException("Missing daily quest rewards");
                quests.add(new DailyQuest(row.quest(),row.npcName(),row.npcCodes(),row.races(),List.copyOf(content.objectives()),
                    row.minLevel(),row.maxLevel(),row.experience(),List.copyOf(items)));
            }
            quests.addAll(experience.quests());
            var npcs=new ArrayList<>(legacy.npcs()); npcs.addAll(experience.npcs());
            coupons=new CouponCatalog(List.copyOf(quests),List.copyOf(npcs),legacy.sourceHashes());
            for(var row:coupons.quests()) if(!row.quest().equals(definitions.get(row.quest().definitionHash()))) throw new IOException("Unknown daily quest definition");
        }
    }
    public CatalogQuest find(String hash) { return definitions.get(hash); }
    public boolean defaultHidden(String hash) { return !DEFAULT_VISIBLE.contains(definitions.get(hash).code()) && experience.quests().stream().noneMatch(r->r.quest().definitionHash().equals(hash)); }
    public BigDecimal experiencePercent(com.guild.app.quest.dto.QuestDtos.DailyQuest row,int level) {
        var denominator=experience.experienceByLevel().get(level);
        if(row.experience()==null || row.experience().signum()==0 || denominator==null || denominator.signum()<=0) return null;
        return row.experience().multiply(BigDecimal.valueOf(100)).divide(denominator,6,RoundingMode.HALF_UP);
    }
    public boolean daily(String hash) { return coupons.quests().stream().anyMatch(r->r.quest().definitionHash().equals(hash)); }
}
