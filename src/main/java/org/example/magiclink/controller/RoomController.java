package org.example.magiclink.controller;

import lombok.Data;
import org.example.magiclink.entity.Room;
import org.example.magiclink.service.RoomManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomManager roomManager;

    public RoomController(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest request) {
        try {
            Room room = roomManager.createRoom(
                    request.getRoomId(),
                    request.getName(),
                    request.getDescription(),
                    request.getType() != null ? request.getType() : Room.RoomType.PUBLIC,
                    request.getPassword(),
                    request.getCreatedBy(),
                    request.isPersistent()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Room created successfully",
                    "room", room
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listRooms() {
        List<Room> rooms = roomManager.getAllRooms();

        List<Map<String, Object>> roomList = rooms.stream()
                .map(room -> {
                    Map<String, Object> roomMap = new HashMap<>();
                    roomMap.put("roomId", room.getRoomId());
                    roomMap.put("name", room.getName());
                    roomMap.put("description", room.getDescription() != null ? room.getDescription() : "");
                    roomMap.put("type", room.getType().toString());
                    roomMap.put("memberCount", roomManager.getRoomMembers(room.getRoomId()).size());
                    roomMap.put("persistent", room.isPersistent());
                    roomMap.put("createdBy", room.getCreatedBy());
                    roomMap.put("createdAt", room.getCreatedAt().toString());
                    return roomMap;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "rooms", roomList
        ));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable String roomId) {
        Optional<Room> roomOpt = roomManager.getRoomById(roomId);

        if (roomOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Room room = roomOpt.get();
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("roomId", room.getRoomId());
        roomData.put("name", room.getName());
        roomData.put("description", room.getDescription() != null ? room.getDescription() : "");
        roomData.put("type", room.getType().toString());
        roomData.put("memberCount", roomManager.getRoomMembers(room.getRoomId()).size());
        roomData.put("members", roomManager.getRoomMembers(room.getRoomId()));
        roomData.put("persistent", room.isPersistent());
        roomData.put("createdBy", room.getCreatedBy());
        roomData.put("createdAt", room.getCreatedAt().toString());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "room", roomData
        ));
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<?> getRoomMembers(@PathVariable String roomId) {
        Set<String> members = roomManager.getRoomMembers(roomId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "roomId", roomId,
                "members", members
        ));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomId) {
        boolean deleted = roomManager.deleteRoom(roomId);

        if (deleted) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Room deleted successfully"
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Data
    public static class CreateRoomRequest {
        private String roomId;
        private String name;
        private String description;
        private Room.RoomType type;
        private String password;
        private String createdBy;
        private boolean persistent;
    }
}
