package org.example.magiclink.websocket.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JoinRoomMessage.class, name = "join_room"),
        @JsonSubTypes.Type(value = LeaveRoomMessage.class, name = "leave_room"),
        @JsonSubTypes.Type(value = RoomMessage.class, name = "room_message"),
        @JsonSubTypes.Type(value = DirectMessage.class, name = "direct_message"),
        @JsonSubTypes.Type(value = BroadcastMessage.class, name = "broadcast"),
        @JsonSubTypes.Type(value = ListRoomsMessage.class, name = "list_rooms"),
        @JsonSubTypes.Type(value = GetRoomMembersMessage.class, name = "get_room_members")
})
public abstract class WebSocketMessage {
    private String type;
}
