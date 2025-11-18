package org.example.magiclink.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.magiclink.entity.Room;
import org.example.magiclink.service.RoomManager;
import org.example.magiclink.websocket.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private RoomManager roomManager;

    @Mock
    private WebSocketSession session;

    private ChatWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ChatWebSocketHandler(roomManager, objectMapper);

        when(session.getId()).thenReturn("session-123");
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void testAfterConnectionEstablished_WithClientId() throws Exception {
        // Given
        URI uri = new URI("ws://localhost:8080/ws/chat?client=user123");
        when(session.getUri()).thenReturn(uri);

        // When
        handler.afterConnectionEstablished(session);

        // Then
        verify(roomManager).registerSession(session, "user123");
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("connected", response.getType());
        assertTrue(response.isSuccess());
    }

    @Test
    void testAfterConnectionEstablished_WithoutClientId() throws Exception {
        // Given
        URI uri = new URI("ws://localhost:8080/ws/chat");
        when(session.getUri()).thenReturn(uri);

        // When
        handler.afterConnectionEstablished(session);

        // Then
        verify(session).close(any(CloseStatus.class));
        verify(roomManager, never()).registerSession(any(), any());
    }

    @Test
    void testAfterConnectionEstablished_AutoJoinRoom() throws Exception {
        // Given
        URI uri = new URI("ws://localhost:8080/ws/chat?client=user123&room=general");
        when(session.getUri()).thenReturn(uri);
        when(roomManager.joinRoom("user123", "general", null)).thenReturn(true);

        // When
        handler.afterConnectionEstablished(session);

        // Then
        verify(roomManager).registerSession(session, "user123");
        verify(roomManager).joinRoom("user123", "general", null);
        verify(session, atLeast(2)).send(any(TextMessage.class)); // Welcome + room joined
    }

    @Test
    void testHandleTextMessage_JoinRoom() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.joinRoom("user123", "general", null)).thenReturn(true);
        when(roomManager.getRoomMembers("general")).thenReturn(Set.of("user123"));

        JoinRoomMessage joinMsg = new JoinRoomMessage();
        joinMsg.setType("join_room");
        joinMsg.setRoom("general");

        String json = objectMapper.writeValueAsString(joinMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(roomManager).joinRoom("user123", "general", null);
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).send(messageCaptor.capture());

        // Verify response
        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("room_joined", response.getType());
        assertTrue(response.isSuccess());
    }

    @Test
    void testHandleTextMessage_JoinRoomWithPassword() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.joinRoom("user123", "private-room", "secret123")).thenReturn(true);
        when(roomManager.getRoomMembers("private-room")).thenReturn(Set.of("user123"));

        JoinRoomMessage joinMsg = new JoinRoomMessage();
        joinMsg.setType("join_room");
        joinMsg.setRoom("private-room");
        joinMsg.setPassword("secret123");

        String json = objectMapper.writeValueAsString(joinMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(roomManager).joinRoom("user123", "private-room", "secret123");
    }

    @Test
    void testHandleTextMessage_LeaveRoom() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.leaveRoom("user123", "general")).thenReturn(true);

        LeaveRoomMessage leaveMsg = new LeaveRoomMessage();
        leaveMsg.setType("leave_room");
        leaveMsg.setRoom("general");

        String json = objectMapper.writeValueAsString(leaveMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(roomManager).leaveRoom("user123", "general");
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("room_left", response.getType());
        assertTrue(response.isSuccess());
    }

    @Test
    void testHandleTextMessage_RoomMessage() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getClientRooms("user123")).thenReturn(Set.of("general"));

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.isOpen()).thenReturn(true);
        when(roomManager.getRoomSessions("general")).thenReturn(Arrays.asList(session, session2));

        RoomMessage roomMsg = new RoomMessage();
        roomMsg.setType("room_message");
        roomMsg.setRoom("general");
        roomMsg.setData("Hello everyone!");

        String json = objectMapper.writeValueAsString(roomMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(session).send(any(TextMessage.class));
        verify(session2).send(any(TextMessage.class));
    }

    @Test
    void testHandleTextMessage_RoomMessage_NotInRoom() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getClientRooms("user123")).thenReturn(Collections.emptySet()); // Not in any room

        RoomMessage roomMsg = new RoomMessage();
        roomMsg.setType("room_message");
        roomMsg.setRoom("general");
        roomMsg.setData("Hello!");

        String json = objectMapper.writeValueAsString(roomMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(roomManager, never()).getRoomSessions(any());
        verify(session, never()).send(any());
    }

    @Test
    void testHandleTextMessage_DirectMessage() throws Exception {
        // Given
        WebSocketSession targetSession = mock(WebSocketSession.class);
        when(targetSession.isOpen()).thenReturn(true);

        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getSessionByClientId("user456")).thenReturn(targetSession);

        DirectMessage directMsg = new DirectMessage();
        directMsg.setType("direct_message");
        directMsg.setTo("user456");
        directMsg.setData("Private message");

        String json = objectMapper.writeValueAsString(directMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(targetSession).send(any(TextMessage.class));
    }

    @Test
    void testHandleTextMessage_DirectMessage_RecipientNotFound() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getSessionByClientId("non-existent")).thenReturn(null);

        DirectMessage directMsg = new DirectMessage();
        directMsg.setType("direct_message");
        directMsg.setTo("non-existent");
        directMsg.setData("Private message");

        String json = objectMapper.writeValueAsString(directMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then - should not throw exception, just log warning
        verify(session, never()).send(any());
    }

    @Test
    void testHandleTextMessage_Broadcast() throws Exception {
        // Given
        WebSocketSession session2 = mock(WebSocketSession.class);
        WebSocketSession session3 = mock(WebSocketSession.class);
        when(session2.isOpen()).thenReturn(true);
        when(session3.isOpen()).thenReturn(true);

        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getAllSessions()).thenReturn(Arrays.asList(session, session2, session3));

        BroadcastMessage broadcastMsg = new BroadcastMessage();
        broadcastMsg.setType("broadcast");
        broadcastMsg.setData("Broadcast to everyone!");

        String json = objectMapper.writeValueAsString(broadcastMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(session).send(any(TextMessage.class));
        verify(session2).send(any(TextMessage.class));
        verify(session3).send(any(TextMessage.class));
    }

    @Test
    void testHandleTextMessage_ListRooms() throws Exception {
        // Given
        Room room1 = new Room();
        room1.setRoomId("room1");
        room1.setName("Room 1");
        room1.setType(Room.RoomType.PUBLIC);
        room1.setPersistent(true);

        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getAllRooms()).thenReturn(Arrays.asList(room1));
        when(roomManager.getRoomMembers("room1")).thenReturn(Set.of("user1", "user2"));

        ListRoomsMessage listMsg = new ListRoomsMessage();
        listMsg.setType("list_rooms");

        String json = objectMapper.writeValueAsString(listMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("rooms_list", response.getType());
        assertTrue(response.isSuccess());
    }

    @Test
    void testHandleTextMessage_GetRoomMembers() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.getRoomMembers("general")).thenReturn(Set.of("user1", "user2", "user3"));

        GetRoomMembersMessage getMembersMsg = new GetRoomMembersMessage();
        getMembersMsg.setType("get_room_members");
        getMembersMsg.setRoom("general");

        String json = objectMapper.writeValueAsString(getMembersMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("room_members", response.getType());
        assertTrue(response.isSuccess());
    }

    @Test
    void testHandleTextMessage_LegacyMessage() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");

        // Plain text message (not JSON)
        TextMessage message = new TextMessage("Just a plain text message");

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("legacy_message", response.getType());
        assertEquals("Just a plain text message", response.getMessage());
    }

    @Test
    void testAfterConnectionClosed() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");

        // When
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Then
        verify(roomManager).unregisterSession(session);
    }

    @Test
    void testHandleTransportError() throws Exception {
        // Given
        Exception error = new RuntimeException("Connection error");

        // When
        handler.handleTransportError(session, error);

        // Then
        verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void testHandleTextMessage_UnregisteredSession() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn(null);

        JoinRoomMessage joinMsg = new JoinRoomMessage();
        joinMsg.setType("join_room");
        joinMsg.setRoom("general");

        String json = objectMapper.writeValueAsString(joinMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then - should not process the message
        verify(roomManager, never()).joinRoom(any(), any(), any());
        verify(session, never()).send(any());
    }

    @Test
    void testJoinRoom_Failed() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.joinRoom("user123", "private-room", null)).thenReturn(false);

        JoinRoomMessage joinMsg = new JoinRoomMessage();
        joinMsg.setType("join_room");
        joinMsg.setRoom("private-room");

        String json = objectMapper.writeValueAsString(joinMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("error", response.getType());
        assertFalse(response.isSuccess());
    }

    @Test
    void testLeaveRoom_Failed() throws Exception {
        // Given
        when(roomManager.getClientId(session)).thenReturn("user123");
        when(roomManager.leaveRoom("user123", "general")).thenReturn(false);

        LeaveRoomMessage leaveMsg = new LeaveRoomMessage();
        leaveMsg.setType("leave_room");
        leaveMsg.setRoom("general");

        String json = objectMapper.writeValueAsString(leaveMsg);
        TextMessage message = new TextMessage(json);

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        ResponseMessage response = objectMapper.readValue(payload, ResponseMessage.class);
        assertEquals("error", response.getType());
        assertFalse(response.isSuccess());
    }
}
