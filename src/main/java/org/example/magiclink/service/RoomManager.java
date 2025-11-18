package org.example.magiclink.service;

import lombok.extern.slf4j.Slf4j;
import org.example.magiclink.entity.Room;
import org.example.magiclink.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RoomManager {

    private final RoomRepository roomRepository;

    // In-memory tracking of active connections
    // roomId -> Set of WebSocketSession IDs
    private final Map<String, Set<String>> roomMembers = new ConcurrentHashMap<>();

    // clientId -> Set of roomIds
    private final Map<String, Set<String>> clientRooms = new ConcurrentHashMap<>();

    // sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // sessionId -> clientId
    private final Map<String, String> sessionToClient = new ConcurrentHashMap<>();

    public RoomManager(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * Register a WebSocket session
     */
    public void registerSession(WebSocketSession session, String clientId) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionToClient.put(sessionId, clientId);
        clientRooms.putIfAbsent(clientId, ConcurrentHashMap.newKeySet());
        log.info("Registered session {} for client {}", sessionId, clientId);
    }

    /**
     * Unregister a WebSocket session and clean up room memberships
     */
    public void unregisterSession(WebSocketSession session) {
        String sessionId = session.getId();
        String clientId = sessionToClient.remove(sessionId);
        sessions.remove(sessionId);

        if (clientId != null) {
            Set<String> rooms = clientRooms.remove(clientId);
            if (rooms != null) {
                for (String roomId : rooms) {
                    leaveRoom(clientId, roomId);
                }
            }
            log.info("Unregistered session {} for client {}", sessionId, clientId);
        }
    }

    /**
     * Create a new room
     */
    public Room createRoom(String roomId, String name, String description, Room.RoomType type,
                          String password, String createdBy, boolean persistent) {
        if (roomRepository.existsByRoomId(roomId)) {
            throw new IllegalArgumentException("Room with ID " + roomId + " already exists");
        }

        Room room = new Room();
        room.setRoomId(roomId);
        room.setName(name);
        room.setDescription(description);
        room.setType(type);
        room.setPassword(password);
        room.setCreatedBy(createdBy);
        room.setPersistent(persistent);

        room = roomRepository.save(room);
        roomMembers.putIfAbsent(roomId, ConcurrentHashMap.newKeySet());

        log.info("Created room: {} ({})", name, roomId);
        return room;
    }

    /**
     * Join a room
     */
    public boolean joinRoom(String clientId, String roomId, String password) {
        // Check if room exists in database
        Optional<Room> roomOpt = roomRepository.findByRoomId(roomId);

        if (roomOpt.isEmpty()) {
            log.warn("Client {} attempted to join non-existent room {}", clientId, roomId);
            return false;
        }

        Room room = roomOpt.get();

        // Check password for private rooms
        if (room.getType() == Room.RoomType.PRIVATE) {
            if (room.getPassword() != null && !room.getPassword().equals(password)) {
                log.warn("Client {} failed password check for room {}", clientId, roomId);
                return false;
            }
        }

        // Add to in-memory tracking
        roomMembers.putIfAbsent(roomId, ConcurrentHashMap.newKeySet());
        roomMembers.get(roomId).add(clientId);

        clientRooms.putIfAbsent(clientId, ConcurrentHashMap.newKeySet());
        clientRooms.get(clientId).add(roomId);

        // Update last activity
        room.setLastActivityAt(LocalDateTime.now());
        roomRepository.save(room);

        log.info("Client {} joined room {}", clientId, roomId);
        return true;
    }

    /**
     * Leave a room
     */
    public boolean leaveRoom(String clientId, String roomId) {
        Set<String> members = roomMembers.get(roomId);
        if (members != null) {
            members.remove(clientId);

            // Clean up empty non-persistent rooms
            if (members.isEmpty()) {
                Optional<Room> roomOpt = roomRepository.findByRoomId(roomId);
                if (roomOpt.isPresent() && !roomOpt.get().isPersistent()) {
                    roomMembers.remove(roomId);
                    roomRepository.delete(roomOpt.get());
                    log.info("Deleted empty non-persistent room {}", roomId);
                }
            }
        }

        Set<String> rooms = clientRooms.get(clientId);
        if (rooms != null) {
            rooms.remove(roomId);
        }

        log.info("Client {} left room {}", clientId, roomId);
        return true;
    }

    /**
     * Get all members of a room
     */
    public Set<String> getRoomMembers(String roomId) {
        return new HashSet<>(roomMembers.getOrDefault(roomId, Collections.emptySet()));
    }

    /**
     * Get all rooms a client is in
     */
    public Set<String> getClientRooms(String clientId) {
        return new HashSet<>(clientRooms.getOrDefault(clientId, Collections.emptySet()));
    }

    /**
     * Get all active sessions in a room
     */
    public List<WebSocketSession> getRoomSessions(String roomId) {
        Set<String> members = roomMembers.get(roomId);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        List<WebSocketSession> roomSessions = new ArrayList<>();
        for (String clientId : members) {
            // Find sessions for this client
            for (Map.Entry<String, String> entry : sessionToClient.entrySet()) {
                if (entry.getValue().equals(clientId)) {
                    WebSocketSession session = sessions.get(entry.getKey());
                    if (session != null && session.isOpen()) {
                        roomSessions.add(session);
                    }
                }
            }
        }
        return roomSessions;
    }

    /**
     * Get session by client ID
     */
    public WebSocketSession getSessionByClientId(String clientId) {
        for (Map.Entry<String, String> entry : sessionToClient.entrySet()) {
            if (entry.getValue().equals(clientId)) {
                return sessions.get(entry.getKey());
            }
        }
        return null;
    }

    /**
     * Get all sessions
     */
    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Get all rooms
     */
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    /**
     * Get room by ID
     */
    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findByRoomId(roomId);
    }

    /**
     * Delete a room
     */
    public boolean deleteRoom(String roomId) {
        Optional<Room> roomOpt = roomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            return false;
        }

        // Remove all members
        Set<String> members = roomMembers.remove(roomId);
        if (members != null) {
            for (String clientId : members) {
                Set<String> rooms = clientRooms.get(clientId);
                if (rooms != null) {
                    rooms.remove(roomId);
                }
            }
        }

        roomRepository.delete(roomOpt.get());
        log.info("Deleted room {}", roomId);
        return true;
    }

    /**
     * Get client ID by session
     */
    public String getClientId(WebSocketSession session) {
        return sessionToClient.get(session.getId());
    }
}
