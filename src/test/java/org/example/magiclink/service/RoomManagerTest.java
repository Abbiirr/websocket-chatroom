package org.example.magiclink.service;

import org.example.magiclink.entity.Room;
import org.example.magiclink.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomManagerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @InjectMocks
    private RoomManager roomManager;

    private Room testRoom;

    @BeforeEach
    void setUp() {
        testRoom = new Room();
        testRoom.setId(1L);
        testRoom.setRoomId("test-room");
        testRoom.setName("Test Room");
        testRoom.setDescription("Test Description");
        testRoom.setType(Room.RoomType.PUBLIC);
        testRoom.setCreatedBy("admin");
        testRoom.setPersistent(true);

        when(session1.getId()).thenReturn("session1");
        when(session1.isOpen()).thenReturn(true);
        when(session2.getId()).thenReturn("session2");
        when(session2.isOpen()).thenReturn(true);
    }

    @Test
    void testRegisterSession() {
        // When
        roomManager.registerSession(session1, "client1");

        // Then
        assertEquals("client1", roomManager.getClientId(session1));
    }

    @Test
    void testUnregisterSession() {
        // Given
        roomManager.registerSession(session1, "client1");

        // When
        roomManager.unregisterSession(session1);

        // Then
        assertNull(roomManager.getClientId(session1));
    }

    @Test
    void testCreateRoom() {
        // Given
        when(roomRepository.existsByRoomId("new-room")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Room created = roomManager.createRoom(
                "new-room",
                "New Room",
                "Description",
                Room.RoomType.PUBLIC,
                null,
                "admin",
                true
        );

        // Then
        assertNotNull(created);
        assertEquals("new-room", created.getRoomId());
        assertEquals("New Room", created.getName());
        assertEquals(Room.RoomType.PUBLIC, created.getType());
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void testCreateRoom_AlreadyExists() {
        // Given
        when(roomRepository.existsByRoomId("existing-room")).thenReturn(true);

        // Then
        assertThrows(IllegalArgumentException.class, () -> {
            roomManager.createRoom(
                    "existing-room",
                    "Existing Room",
                    "Description",
                    Room.RoomType.PUBLIC,
                    null,
                    "admin",
                    true
            );
        });
    }

    @Test
    void testJoinRoom_Success() {
        // Given
        when(roomRepository.findByRoomId("test-room")).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);

        // When
        boolean result = roomManager.joinRoom("client1", "test-room", null);

        // Then
        assertTrue(result);
        assertTrue(roomManager.getRoomMembers("test-room").contains("client1"));
        assertTrue(roomManager.getClientRooms("client1").contains("test-room"));
    }

    @Test
    void testJoinRoom_NonExistent() {
        // Given
        when(roomRepository.findByRoomId("non-existent")).thenReturn(Optional.empty());

        // When
        boolean result = roomManager.joinRoom("client1", "non-existent", null);

        // Then
        assertFalse(result);
    }

    @Test
    void testJoinRoom_PrivateWithPassword() {
        // Given
        Room privateRoom = new Room();
        privateRoom.setRoomId("private-room");
        privateRoom.setName("Private Room");
        privateRoom.setType(Room.RoomType.PRIVATE);
        privateRoom.setPassword("secret123");
        privateRoom.setCreatedBy("admin");
        privateRoom.setPersistent(true);

        when(roomRepository.findByRoomId("private-room")).thenReturn(Optional.of(privateRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(privateRoom);

        // When - correct password
        boolean successResult = roomManager.joinRoom("client1", "private-room", "secret123");

        // Then
        assertTrue(successResult);

        // When - wrong password
        boolean failResult = roomManager.joinRoom("client2", "private-room", "wrong");

        // Then
        assertFalse(failResult);
    }

    @Test
    void testLeaveRoom() {
        // Given
        when(roomRepository.findByRoomId("test-room")).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);
        roomManager.joinRoom("client1", "test-room", null);

        // When
        boolean result = roomManager.leaveRoom("client1", "test-room");

        // Then
        assertTrue(result);
        assertFalse(roomManager.getRoomMembers("test-room").contains("client1"));
        assertFalse(roomManager.getClientRooms("client1").contains("test-room"));
    }

    @Test
    void testLeaveRoom_DeletesNonPersistentEmptyRoom() {
        // Given
        Room nonPersistentRoom = new Room();
        nonPersistentRoom.setRoomId("temp-room");
        nonPersistentRoom.setName("Temp Room");
        nonPersistentRoom.setType(Room.RoomType.PUBLIC);
        nonPersistentRoom.setCreatedBy("admin");
        nonPersistentRoom.setPersistent(false);

        when(roomRepository.findByRoomId("temp-room")).thenReturn(Optional.of(nonPersistentRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(nonPersistentRoom);
        roomManager.joinRoom("client1", "temp-room", null);

        // When
        roomManager.leaveRoom("client1", "temp-room");

        // Then
        verify(roomRepository).delete(nonPersistentRoom);
    }

    @Test
    void testGetRoomMembers() {
        // Given
        when(roomRepository.findByRoomId("test-room")).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);
        roomManager.joinRoom("client1", "test-room", null);
        roomManager.joinRoom("client2", "test-room", null);

        // When
        Set<String> members = roomManager.getRoomMembers("test-room");

        // Then
        assertEquals(2, members.size());
        assertTrue(members.contains("client1"));
        assertTrue(members.contains("client2"));
    }

    @Test
    void testGetClientRooms() {
        // Given
        Room room1 = new Room();
        room1.setRoomId("room1");
        room1.setName("Room 1");
        room1.setType(Room.RoomType.PUBLIC);
        room1.setCreatedBy("admin");
        room1.setPersistent(true);

        Room room2 = new Room();
        room2.setRoomId("room2");
        room2.setName("Room 2");
        room2.setType(Room.RoomType.PUBLIC);
        room2.setCreatedBy("admin");
        room2.setPersistent(true);

        when(roomRepository.findByRoomId("room1")).thenReturn(Optional.of(room1));
        when(roomRepository.findByRoomId("room2")).thenReturn(Optional.of(room2));
        when(roomRepository.save(any(Room.class))).thenReturn(room1, room2);

        roomManager.joinRoom("client1", "room1", null);
        roomManager.joinRoom("client1", "room2", null);

        // When
        Set<String> rooms = roomManager.getClientRooms("client1");

        // Then
        assertEquals(2, rooms.size());
        assertTrue(rooms.contains("room1"));
        assertTrue(rooms.contains("room2"));
    }

    @Test
    void testGetRoomSessions() {
        // Given
        when(roomRepository.findByRoomId("test-room")).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);

        roomManager.registerSession(session1, "client1");
        roomManager.registerSession(session2, "client2");
        roomManager.joinRoom("client1", "test-room", null);
        roomManager.joinRoom("client2", "test-room", null);

        // When
        List<WebSocketSession> sessions = roomManager.getRoomSessions("test-room");

        // Then
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains(session1));
        assertTrue(sessions.contains(session2));
    }

    @Test
    void testGetSessionByClientId() {
        // Given
        roomManager.registerSession(session1, "client1");

        // When
        WebSocketSession result = roomManager.getSessionByClientId("client1");

        // Then
        assertEquals(session1, result);
    }

    @Test
    void testDeleteRoom() {
        // Given
        when(roomRepository.findByRoomId("test-room")).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);
        roomManager.joinRoom("client1", "test-room", null);

        // When
        boolean result = roomManager.deleteRoom("test-room");

        // Then
        assertTrue(result);
        verify(roomRepository).delete(testRoom);
        assertFalse(roomManager.getClientRooms("client1").contains("test-room"));
    }

    @Test
    void testDeleteRoom_NonExistent() {
        // Given
        when(roomRepository.findByRoomId("non-existent")).thenReturn(Optional.empty());

        // When
        boolean result = roomManager.deleteRoom("non-existent");

        // Then
        assertFalse(result);
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void testUnregisterSession_LeavesAllRooms() {
        // Given
        when(roomRepository.findByRoomId("room1")).thenReturn(Optional.of(testRoom));
        when(roomRepository.findByRoomId("room2")).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);

        roomManager.registerSession(session1, "client1");
        roomManager.joinRoom("client1", "room1", null);
        roomManager.joinRoom("client1", "room2", null);

        // When
        roomManager.unregisterSession(session1);

        // Then
        assertEquals(0, roomManager.getClientRooms("client1").size());
    }

    @Test
    void testGetAllRooms() {
        // Given
        List<Room> rooms = Arrays.asList(testRoom);
        when(roomRepository.findAll()).thenReturn(rooms);

        // When
        List<Room> result = roomManager.getAllRooms();

        // Then
        assertEquals(1, result.size());
        assertEquals(testRoom, result.get(0));
    }

    @Test
    void testGetRoomById() {
        // Given
        when(roomRepository.findByRoomId("test-room")).thenReturn(Optional.of(testRoom));

        // When
        Optional<Room> result = roomManager.getRoomById("test-room");

        // Then
        assertTrue(result.isPresent());
        assertEquals(testRoom, result.get());
    }
}
