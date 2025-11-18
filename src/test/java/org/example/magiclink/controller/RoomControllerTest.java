package org.example.magiclink.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.magiclink.entity.Room;
import org.example.magiclink.service.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
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
        testRoom.setCreatedAt(LocalDateTime.now());
        testRoom.setLastActivityAt(LocalDateTime.now());
    }

    @Test
    void testCreateRoom_Success() throws Exception {
        // Given
        RoomController.CreateRoomRequest request = new RoomController.CreateRoomRequest();
        request.setRoomId("new-room");
        request.setName("New Room");
        request.setDescription("New Description");
        request.setType(Room.RoomType.PUBLIC);
        request.setCreatedBy("admin");
        request.setPersistent(true);

        when(roomManager.createRoom(
                anyString(), anyString(), anyString(), any(Room.RoomType.class),
                anyString(), anyString(), anyBoolean()
        )).thenReturn(testRoom);

        // When & Then
        mockMvc.perform(post("/api/v1/rooms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room created successfully"))
                .andExpect(jsonPath("$.room.roomId").value("test-room"));
    }

    @Test
    void testCreateRoom_AlreadyExists() throws Exception {
        // Given
        RoomController.CreateRoomRequest request = new RoomController.CreateRoomRequest();
        request.setRoomId("existing-room");
        request.setName("Existing Room");
        request.setType(Room.RoomType.PUBLIC);
        request.setCreatedBy("admin");
        request.setPersistent(true);

        when(roomManager.createRoom(
                anyString(), anyString(), anyString(), any(Room.RoomType.class),
                anyString(), anyString(), anyBoolean()
        )).thenThrow(new IllegalArgumentException("Room already exists"));

        // When & Then
        mockMvc.perform(post("/api/v1/rooms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Room already exists"));
    }

    @Test
    void testListRooms() throws Exception {
        // Given
        List<Room> rooms = Arrays.asList(testRoom);
        when(roomManager.getAllRooms()).thenReturn(rooms);
        when(roomManager.getRoomMembers("test-room")).thenReturn(Set.of("client1", "client2"));

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rooms").isArray())
                .andExpect(jsonPath("$.rooms[0].roomId").value("test-room"))
                .andExpect(jsonPath("$.rooms[0].name").value("Test Room"))
                .andExpect(jsonPath("$.rooms[0].type").value("PUBLIC"))
                .andExpect(jsonPath("$.rooms[0].memberCount").value(2))
                .andExpect(jsonPath("$.rooms[0].persistent").value(true));
    }

    @Test
    void testListRooms_Empty() throws Exception {
        // Given
        when(roomManager.getAllRooms()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rooms").isArray())
                .andExpect(jsonPath("$.rooms").isEmpty());
    }

    @Test
    void testGetRoom_Success() throws Exception {
        // Given
        when(roomManager.getRoomById("test-room")).thenReturn(Optional.of(testRoom));
        when(roomManager.getRoomMembers("test-room")).thenReturn(Set.of("client1", "client2"));

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/test-room"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.room.roomId").value("test-room"))
                .andExpect(jsonPath("$.room.name").value("Test Room"))
                .andExpect(jsonPath("$.room.description").value("Test Description"))
                .andExpect(jsonPath("$.room.type").value("PUBLIC"))
                .andExpect(jsonPath("$.room.memberCount").value(2))
                .andExpect(jsonPath("$.room.members").isArray())
                .andExpect(jsonPath("$.room.persistent").value(true))
                .andExpect(jsonPath("$.room.createdBy").value("admin"));
    }

    @Test
    void testGetRoom_NotFound() throws Exception {
        // Given
        when(roomManager.getRoomById("non-existent")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/non-existent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetRoomMembers() throws Exception {
        // Given
        Set<String> members = Set.of("client1", "client2", "client3");
        when(roomManager.getRoomMembers("test-room")).thenReturn(members);

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/test-room/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.roomId").value("test-room"))
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members", hasSize(3)));
    }

    @Test
    void testGetRoomMembers_EmptyRoom() throws Exception {
        // Given
        when(roomManager.getRoomMembers("empty-room")).thenReturn(Collections.emptySet());

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/empty-room/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.roomId").value("empty-room"))
                .andExpect(jsonPath("$.members").isEmpty());
    }

    @Test
    void testDeleteRoom_Success() throws Exception {
        // Given
        when(roomManager.deleteRoom("test-room")).thenReturn(true);

        // When & Then
        mockMvc.perform(delete("/api/v1/rooms/test-room"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room deleted successfully"));

        verify(roomManager).deleteRoom("test-room");
    }

    @Test
    void testDeleteRoom_NotFound() throws Exception {
        // Given
        when(roomManager.deleteRoom("non-existent")).thenReturn(false);

        // When & Then
        mockMvc.perform(delete("/api/v1/rooms/non-existent"))
                .andExpect(status().isNotFound());

        verify(roomManager).deleteRoom("non-existent");
    }

    @Test
    void testCreateRoom_WithNullType_DefaultsToPublic() throws Exception {
        // Given
        RoomController.CreateRoomRequest request = new RoomController.CreateRoomRequest();
        request.setRoomId("default-room");
        request.setName("Default Room");
        request.setType(null); // No type specified
        request.setCreatedBy("admin");
        request.setPersistent(false);

        when(roomManager.createRoom(
                eq("default-room"),
                eq("Default Room"),
                any(),
                eq(Room.RoomType.PUBLIC), // Should default to PUBLIC
                any(),
                eq("admin"),
                eq(false)
        )).thenReturn(testRoom);

        // When & Then
        mockMvc.perform(post("/api/v1/rooms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(roomManager).createRoom(
                eq("default-room"),
                eq("Default Room"),
                any(),
                eq(Room.RoomType.PUBLIC),
                any(),
                eq("admin"),
                eq(false)
        );
    }

    @Test
    void testCreateRoom_PrivateWithPassword() throws Exception {
        // Given
        Room privateRoom = new Room();
        privateRoom.setRoomId("private-room");
        privateRoom.setName("Private Room");
        privateRoom.setType(Room.RoomType.PRIVATE);
        privateRoom.setPassword("secret123");
        privateRoom.setCreatedBy("admin");
        privateRoom.setPersistent(true);

        RoomController.CreateRoomRequest request = new RoomController.CreateRoomRequest();
        request.setRoomId("private-room");
        request.setName("Private Room");
        request.setType(Room.RoomType.PRIVATE);
        request.setPassword("secret123");
        request.setCreatedBy("admin");
        request.setPersistent(true);

        when(roomManager.createRoom(
                eq("private-room"),
                eq("Private Room"),
                any(),
                eq(Room.RoomType.PRIVATE),
                eq("secret123"),
                eq("admin"),
                eq(true)
        )).thenReturn(privateRoom);

        // When & Then
        mockMvc.perform(post("/api/v1/rooms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.room.type").value("PRIVATE"));
    }

    @Test
    void testListRooms_WithNullDescription() throws Exception {
        // Given
        Room roomWithNullDesc = new Room();
        roomWithNullDesc.setRoomId("no-desc-room");
        roomWithNullDesc.setName("No Description Room");
        roomWithNullDesc.setDescription(null); // Null description
        roomWithNullDesc.setType(Room.RoomType.PUBLIC);
        roomWithNullDesc.setCreatedBy("admin");
        roomWithNullDesc.setPersistent(true);
        roomWithNullDesc.setCreatedAt(LocalDateTime.now());

        when(roomManager.getAllRooms()).thenReturn(Arrays.asList(roomWithNullDesc));
        when(roomManager.getRoomMembers("no-desc-room")).thenReturn(Collections.emptySet());

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rooms[0].description").value(""));
    }

    @Test
    void testGetRoom_WithNullDescription() throws Exception {
        // Given
        Room roomWithNullDesc = new Room();
        roomWithNullDesc.setRoomId("no-desc-room");
        roomWithNullDesc.setName("No Description Room");
        roomWithNullDesc.setDescription(null);
        roomWithNullDesc.setType(Room.RoomType.PUBLIC);
        roomWithNullDesc.setCreatedBy("admin");
        roomWithNullDesc.setPersistent(true);
        roomWithNullDesc.setCreatedAt(LocalDateTime.now());

        when(roomManager.getRoomById("no-desc-room")).thenReturn(Optional.of(roomWithNullDesc));
        when(roomManager.getRoomMembers("no-desc-room")).thenReturn(Collections.emptySet());

        // When & Then
        mockMvc.perform(get("/api/v1/rooms/no-desc-room"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.description").value(""));
    }
}
