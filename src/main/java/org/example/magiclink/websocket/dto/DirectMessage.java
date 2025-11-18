package org.example.magiclink.websocket.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DirectMessage extends WebSocketMessage {
    private String to; // clientId of recipient
    private String data;
}
