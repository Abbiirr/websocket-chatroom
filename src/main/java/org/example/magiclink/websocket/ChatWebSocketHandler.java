package org.example.magiclink.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.magiclink.entity.Room;
import org.example.magiclink.service.RoomManager;
import org.example.magiclink.websocket.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(RoomManager roomManager, ObjectMapper objectMapper) {
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String clientId = extractClientId(session);

        if (clientId == null || clientId.isEmpty()) {
            log.warn("Connection attempt without clientId, closing session");
            session.close(CloseStatus.BAD_DATA.withReason("clientId parameter required"));
            return;
        }

        roomManager.registerSession(session, clientId);

        // Send welcome message
        ResponseMessage welcome = ResponseMessage.success(
                "connected",
                "Connected to chat server",
                Map.of("clientId", clientId)
        );
        sendMessage(session, welcome);

        // Auto-join room if specified in URL
        String autoJoinRoom = extractRoomId(session);
        if (autoJoinRoom != null && !autoJoinRoom.isEmpty()) {
            boolean joined = roomManager.joinRoom(clientId, autoJoinRoom, null);
            if (joined) {
                ResponseMessage roomJoined = ResponseMessage.success(
                        "room_joined",
                        "Joined room: " + autoJoinRoom,
                        Map.of("room", autoJoinRoom)
                );
                sendMessage(session, roomJoined);
            }
        }

        log.info("Client {} connected via session {}", clientId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String clientId = roomManager.getClientId(session);
        if (clientId == null) {
            log.warn("Received message from unregistered session");
            return;
        }

        String payload = message.getPayload();
        log.debug("Received message from {}: {}", clientId, payload);

        try {
            // Try to parse as structured message
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            handleStructuredMessage(session, clientId, wsMessage);
        } catch (Exception e) {
            // If parsing fails, treat as legacy/simple message
            log.debug("Message is not structured JSON, treating as legacy message");
            handleLegacyMessage(session, clientId, payload);
        }
    }

    private void handleStructuredMessage(WebSocketSession session, String clientId, WebSocketMessage message) throws IOException {
        switch (message) {
            case JoinRoomMessage joinMsg -> handleJoinRoom(session, clientId, joinMsg);
            case LeaveRoomMessage leaveMsg -> handleLeaveRoom(session, clientId, leaveMsg);
            case RoomMessage roomMsg -> handleRoomMessage(clientId, roomMsg);
            case DirectMessage directMsg -> handleDirectMessage(clientId, directMsg);
            case BroadcastMessage broadcastMsg -> handleBroadcast(clientId, broadcastMsg);
            case ListRoomsMessage listMsg -> handleListRooms(session, clientId);
            case GetRoomMembersMessage membersMsg -> handleGetRoomMembers(session, clientId, membersMsg);
            default -> {
                log.warn("Unknown message type from client {}", clientId);
                sendError(session, "Unknown message type");
            }
        }
    }

    private void handleJoinRoom(WebSocketSession session, String clientId, JoinRoomMessage message) throws IOException {
        String roomId = message.getRoom();
        String password = message.getPassword();

        boolean joined = roomManager.joinRoom(clientId, roomId, password);

        if (joined) {
            ResponseMessage response = ResponseMessage.success(
                    "room_joined",
                    "Successfully joined room: " + roomId,
                    Map.of("room", roomId, "members", roomManager.getRoomMembers(roomId))
            );
            sendMessage(session, response);

            // Notify other room members
            notifyRoom(roomId, clientId, ResponseMessage.success(
                    "member_joined",
                    "Member joined the room",
                    Map.of("room", roomId, "member", clientId)
            ));
        } else {
            sendError(session, "Failed to join room: " + roomId);
        }
    }

    private void handleLeaveRoom(WebSocketSession session, String clientId, LeaveRoomMessage message) throws IOException {
        String roomId = message.getRoom();

        boolean left = roomManager.leaveRoom(clientId, roomId);

        if (left) {
            ResponseMessage response = ResponseMessage.success(
                    "room_left",
                    "Successfully left room: " + roomId,
                    Map.of("room", roomId)
            );
            sendMessage(session, response);

            // Notify other room members
            notifyRoom(roomId, clientId, ResponseMessage.success(
                    "member_left",
                    "Member left the room",
                    Map.of("room", roomId, "member", clientId)
            ));
        } else {
            sendError(session, "Failed to leave room: " + roomId);
        }
    }

    private void handleRoomMessage(String clientId, RoomMessage message) {
        String roomId = message.getRoom();
        String data = message.getData();

        // Verify sender is in the room
        if (!roomManager.getClientRooms(clientId).contains(roomId)) {
            log.warn("Client {} attempted to send message to room {} without being a member", clientId, roomId);
            return;
        }

        // Broadcast to all room members
        ResponseMessage response = ResponseMessage.success(
                "room_message",
                data,
                Map.of("room", roomId, "from", clientId, "timestamp", System.currentTimeMillis())
        );

        for (WebSocketSession session : roomManager.getRoomSessions(roomId)) {
            try {
                sendMessage(session, response);
            } catch (IOException e) {
                log.error("Failed to send room message to session", e);
            }
        }
    }

    private void handleDirectMessage(String clientId, DirectMessage message) {
        String targetClientId = message.getTo();
        String data = message.getData();

        WebSocketSession targetSession = roomManager.getSessionByClientId(targetClientId);
        if (targetSession != null && targetSession.isOpen()) {
            ResponseMessage response = ResponseMessage.success(
                    "direct_message",
                    data,
                    Map.of("from", clientId, "timestamp", System.currentTimeMillis())
            );
            try {
                sendMessage(targetSession, response);
            } catch (IOException e) {
                log.error("Failed to send direct message", e);
            }
        } else {
            log.warn("Target client {} not found or not connected", targetClientId);
        }
    }

    private void handleBroadcast(String clientId, BroadcastMessage message) {
        String data = message.getData();

        ResponseMessage response = ResponseMessage.success(
                "broadcast",
                data,
                Map.of("from", clientId, "timestamp", System.currentTimeMillis())
        );

        for (WebSocketSession session : roomManager.getAllSessions()) {
            if (session.isOpen()) {
                try {
                    sendMessage(session, response);
                } catch (IOException e) {
                    log.error("Failed to send broadcast message", e);
                }
            }
        }
    }

    private void handleListRooms(WebSocketSession session, String clientId) throws IOException {
        List<Room> rooms = roomManager.getAllRooms();

        List<Map<String, Object>> roomList = rooms.stream()
                .map(room -> {
                    Map<String, Object> roomMap = new HashMap<>();
                    roomMap.put("roomId", room.getRoomId());
                    roomMap.put("name", room.getName());
                    roomMap.put("type", room.getType().toString());
                    roomMap.put("memberCount", roomManager.getRoomMembers(room.getRoomId()).size());
                    roomMap.put("persistent", room.isPersistent());
                    return roomMap;
                })
                .toList();

        ResponseMessage response = ResponseMessage.success(
                "rooms_list",
                "Available rooms",
                Map.of("rooms", roomList)
        );
        sendMessage(session, response);
    }

    private void handleGetRoomMembers(WebSocketSession session, String clientId, GetRoomMembersMessage message) throws IOException {
        String roomId = message.getRoom();
        Set<String> members = roomManager.getRoomMembers(roomId);

        ResponseMessage response = ResponseMessage.success(
                "room_members",
                "Members of room: " + roomId,
                Map.of("room", roomId, "members", members)
        );
        sendMessage(session, response);
    }

    private void handleLegacyMessage(WebSocketSession session, String clientId, String payload) throws IOException {
        // For backward compatibility - treat plain text messages as broadcasts
        log.info("Legacy message from {}: {}", clientId, payload);

        ResponseMessage response = ResponseMessage.success(
                "legacy_message",
                payload,
                Map.of("from", clientId, "timestamp", System.currentTimeMillis())
        );
        sendMessage(session, response);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String clientId = roomManager.getClientId(session);
        roomManager.unregisterSession(session);
        log.info("Client {} disconnected: {}", clientId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error for session {}", session.getId(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }

    // Helper methods

    private String extractClientId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;

        Map<String, String> params = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .toSingleValueMap();

        return params.get("client");
    }

    private String extractRoomId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;

        Map<String, String> params = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .toSingleValueMap();

        return params.get("room");
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        ResponseMessage response = ResponseMessage.error("error", errorMessage);
        sendMessage(session, response);
    }

    private void notifyRoom(String roomId, String excludeClientId, ResponseMessage message) {
        for (WebSocketSession session : roomManager.getRoomSessions(roomId)) {
            String sessionClientId = roomManager.getClientId(session);
            if (!excludeClientId.equals(sessionClientId)) {
                try {
                    sendMessage(session, message);
                } catch (IOException e) {
                    log.error("Failed to notify room member", e);
                }
            }
        }
    }
}
