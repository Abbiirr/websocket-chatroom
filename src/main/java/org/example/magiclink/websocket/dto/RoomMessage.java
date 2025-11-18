package org.example.magiclink.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoomMessage extends WebSocketMessage {
    private String room;
    private String data;
}
