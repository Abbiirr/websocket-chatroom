package org.example.magiclink.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseMessage {
    private String type;
    private boolean success;
    private String message;
    private Object data;

    public static ResponseMessage success(String type, String message, Object data) {
        return new ResponseMessage(type, true, message, data);
    }

    public static ResponseMessage error(String type, String message) {
        return new ResponseMessage(type, false, message, null);
    }
}
