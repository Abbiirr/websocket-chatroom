package org.example.magiclink.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetRoomMembersMessage extends WebSocketMessage {
    private String room;
}
