package com.guild.app.websocket;

import com.guild.app.common.enums.PartyMap;
import com.guild.app.log.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishLog(AuditLogResponse log) {
        messagingTemplate.convertAndSend("/topic/logs", log);
    }

    public void publishEvent(Object payload) {
        messagingTemplate.convertAndSend("/topic/events", payload);
    }

    public void publishAuction(Long auctionId, Object payload) {
        messagingTemplate.convertAndSend("/topic/auctions/" + auctionId, payload);
    }

    public void publishAuctionList(Object payload) {
        messagingTemplate.convertAndSend("/topic/auctions", payload);
    }

    public void publishPointsUpdate(Long memberId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/points/" + memberId, payload);
    }

    public void publishPartyBoard(PartyMap map) {
        messagingTemplate.convertAndSend("/topic/parties/" + map.name(), Map.of("type", "BOARD_UPDATED", "map", map.name()));
    }
}
