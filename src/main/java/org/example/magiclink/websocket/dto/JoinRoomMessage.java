package org.example.magiclink.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JoinRoomMessage extends WebSocketMessage {
    private String room;
    private String password; // Optional, for private rooms
}
