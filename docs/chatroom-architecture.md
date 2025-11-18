# WebSocket Chatroom Architecture

## Overview

This document describes the WebSocket-based chatroom functionality added to the Magic Link Service. The implementation provides real-time multi-channel communication while maintaining full backward compatibility with existing features.

## Features

### Core Capabilities

1. **Multi-Room Support**: Users can create and join multiple chat rooms
2. **Real-Time Communication**: WebSocket-based bidirectional messaging
3. **Multiple Message Types**:
   - Room messages (broadcast to all room members)
   - Direct messages (one-to-one communication)
   - Broadcast messages (to all connected clients)
4. **Room Management**:
   - Public and private rooms
   - Persistent and ephemeral rooms
   - Password-protected private rooms
5. **Member Tracking**: Real-time tracking of room membership
6. **Backward Compatible**: Existing functionality remains unchanged

## Architecture

### Components

```
┌─────────────────────┐
│   WebSocket Client  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ ChatWebSocketHandler│ (Connection & Message Routing)
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    RoomManager      │ (Room Membership & State)
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   Room Repository   │ (Persistence Layer)
└─────────────────────┘
```

### Key Classes

#### 1. WebSocketConfig
- Location: `config/WebSocketConfig.java`
- Purpose: Configures WebSocket endpoint at `/ws/chat`
- Registers the ChatWebSocketHandler

#### 2. ChatWebSocketHandler
- Location: `websocket/ChatWebSocketHandler.java`
- Purpose: Handles WebSocket connections and message routing
- Features:
  - Connection management (connect/disconnect)
  - Message parsing and routing
  - Auto-join rooms via URL parameter
  - Backward compatibility for legacy messages

#### 3. RoomManager
- Location: `service/RoomManager.java`
- Purpose: Manages room membership and session tracking
- Features:
  - In-memory session tracking
  - Room member management
  - Room lifecycle (create, join, leave, delete)
  - Auto-cleanup of empty non-persistent rooms

#### 4. Room Entity
- Location: `entity/Room.java`
- Purpose: Persistent storage of room metadata
- Fields:
  - `roomId`: Unique identifier
  - `name`: Display name
  - `description`: Room description
  - `type`: PUBLIC or PRIVATE
  - `password`: Optional password for private rooms
  - `createdBy`: Creator's client ID
  - `persistent`: Whether room survives when empty

#### 5. RoomController
- Location: `controller/RoomController.java`
- Purpose: HTTP REST API for room management
- Endpoints:
  - `POST /api/v1/rooms/create` - Create a new room
  - `GET /api/v1/rooms/list` - List all rooms
  - `GET /api/v1/rooms/{roomId}` - Get room details
  - `GET /api/v1/rooms/{roomId}/members` - Get room members
  - `DELETE /api/v1/rooms/{roomId}` - Delete a room

## Message Protocol

### Connection

Connect to WebSocket endpoint:
```
ws://server/ws/chat?client=<clientId>&room=<roomId>
```

Parameters:
- `client` (required): Unique client identifier
- `room` (optional): Auto-join this room on connection

### Message Types

#### 1. Join Room
```json
{
  "type": "join_room",
  "room": "general",
  "password": "optional_password"
}
```

#### 2. Leave Room
```json
{
  "type": "leave_room",
  "room": "general"
}
```

#### 3. Room Message
```json
{
  "type": "room_message",
  "room": "general",
  "data": "Hello everyone!"
}
```

#### 4. Direct Message
```json
{
  "type": "direct_message",
  "to": "user_123",
  "data": "Private message"
}
```

#### 5. Broadcast Message
```json
{
  "type": "broadcast",
  "data": "Message to all connected clients"
}
```

#### 6. List Rooms
```json
{
  "type": "list_rooms"
}
```

#### 7. Get Room Members
```json
{
  "type": "get_room_members",
  "room": "general"
}
```

### Response Messages

#### Connected
```json
{
  "type": "connected",
  "success": true,
  "message": "Connected to chat server",
  "data": {
    "clientId": "user_123"
  }
}
```

#### Room Joined
```json
{
  "type": "room_joined",
  "success": true,
  "message": "Successfully joined room: general",
  "data": {
    "room": "general",
    "members": ["user_123", "user_456"]
  }
}
```

#### Room Message
```json
{
  "type": "room_message",
  "success": true,
  "message": "Hello everyone!",
  "data": {
    "room": "general",
    "from": "user_123",
    "timestamp": 1699564800000
  }
}
```

#### Member Joined/Left
```json
{
  "type": "member_joined",
  "success": true,
  "message": "Member joined the room",
  "data": {
    "room": "general",
    "member": "user_789"
  }
}
```

#### Error
```json
{
  "type": "error",
  "success": false,
  "message": "Failed to join room: general"
}
```

## REST API

### Create Room
```http
POST /api/v1/rooms/create
Content-Type: application/json

{
  "roomId": "general",
  "name": "General Chat",
  "description": "General discussion room",
  "type": "PUBLIC",
  "password": null,
  "createdBy": "user_123",
  "persistent": true
}
```

### List Rooms
```http
GET /api/v1/rooms/list
```

Response:
```json
{
  "success": true,
  "rooms": [
    {
      "roomId": "general",
      "name": "General Chat",
      "description": "General discussion room",
      "type": "PUBLIC",
      "memberCount": 5,
      "persistent": true,
      "createdBy": "user_123",
      "createdAt": "2024-11-18T10:30:00"
    }
  ]
}
```

### Get Room Details
```http
GET /api/v1/rooms/{roomId}
```

### Get Room Members
```http
GET /api/v1/rooms/{roomId}/members
```

Response:
```json
{
  "success": true,
  "roomId": "general",
  "members": ["user_123", "user_456", "user_789"]
}
```

### Delete Room
```http
DELETE /api/v1/rooms/{roomId}
```

## Testing

### Test Client

Access the built-in test client at:
```
http://localhost:8080/chatroom-test
```

Features:
- Connect with custom client ID
- Create and join rooms
- Send room messages, direct messages, and broadcasts
- View room members
- Real-time message updates

### Manual Testing with curl

1. Create a room:
```bash
curl -X POST http://localhost:8080/api/v1/rooms/create \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": "test-room",
    "name": "Test Room",
    "description": "A test room",
    "type": "PUBLIC",
    "createdBy": "admin",
    "persistent": true
  }'
```

2. List rooms:
```bash
curl http://localhost:8080/api/v1/rooms/list
```

3. Get room members:
```bash
curl http://localhost:8080/api/v1/rooms/test-room/members
```

### WebSocket Testing with wscat

```bash
# Install wscat
npm install -g wscat

# Connect to WebSocket
wscat -c "ws://localhost:8080/ws/chat?client=test_user"

# Send messages
{"type":"join_room","room":"general"}
{"type":"room_message","room":"general","data":"Hello!"}
{"type":"leave_room","room":"general"}
```

## Security Considerations

### Current Implementation

1. **CSRF Disabled**: Required for WebSocket functionality
2. **Public Endpoints**: WebSocket and room APIs are publicly accessible
3. **No Authentication**: Client IDs are self-assigned (for testing)

### Production Recommendations

1. **Enable Authentication**:
   - Integrate with existing OAuth2/JWT authentication
   - Validate user identity on WebSocket connection
   - Use authenticated user ID instead of client ID parameter

2. **Authorization**:
   - Implement room-level permissions
   - Restrict room creation to authenticated users
   - Add admin roles for room management

3. **Rate Limiting**:
   - Limit message frequency per client
   - Limit room creation per user
   - Implement connection limits

4. **Input Validation**:
   - Sanitize all message content
   - Validate room IDs and client IDs
   - Prevent XSS attacks in messages

5. **CORS Configuration**:
   - Replace `setAllowedOrigins("*")` with specific origins
   - Use environment-specific configurations

6. **Encryption**:
   - Use WSS (WebSocket Secure) in production
   - Encrypt sensitive message content
   - Hash room passwords

## Database Schema

### Room Table
```sql
CREATE TABLE rooms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(500),
  type VARCHAR(50) NOT NULL,
  password VARCHAR(255),
  created_by VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  last_activity_at TIMESTAMP,
  persistent BOOLEAN NOT NULL DEFAULT FALSE
);
```

## Extension Points

### Adding New Message Types

1. Create a new DTO extending `WebSocketMessage`
2. Add to `@JsonSubTypes` in `WebSocketMessage.java`
3. Add handler method in `ChatWebSocketHandler`

Example:
```java
@Data
@EqualsAndHashCode(callSuper = true)
public class TypingIndicatorMessage extends WebSocketMessage {
    private String room;
    private boolean isTyping;
}
```

### Adding Room Permissions

1. Extend `Room` entity with permission fields
2. Add permission checks in `RoomManager`
3. Update `ChatWebSocketHandler` to enforce permissions

### Adding Message History

1. Create `Message` entity
2. Create `MessageRepository`
3. Persist messages in `ChatWebSocketHandler`
4. Add endpoint to retrieve message history

## Performance Considerations

1. **In-Memory State**: Current implementation uses ConcurrentHashMap for session tracking
   - Scales to ~10K concurrent connections per server
   - Not suitable for distributed deployments

2. **Distributed Deployment**: For horizontal scaling:
   - Use Redis for shared session state
   - Implement message broker (RabbitMQ, Kafka)
   - Use Spring Cloud Data Flow for distributed messaging

3. **Database Optimization**:
   - Index on `room_id` for fast lookups
   - Consider caching room metadata
   - Archive old messages periodically

## Backward Compatibility

The implementation maintains full backward compatibility:

1. **Existing Endpoints**: All original endpoints remain unchanged
2. **Legacy Messages**: Plain text messages are handled as legacy broadcasts
3. **No Breaking Changes**: No modifications to existing code paths
4. **Optional Feature**: Chatroom functionality is opt-in

## Future Enhancements

1. **Presence System**: Online/offline status tracking
2. **Typing Indicators**: Show when users are typing
3. **File Sharing**: Upload and share files in rooms
4. **Message Reactions**: React to messages with emojis
5. **Thread Support**: Reply to specific messages
6. **User Profiles**: Display names, avatars, status messages
7. **Push Notifications**: Notify offline users of new messages
8. **Message Search**: Full-text search across messages
9. **Moderation Tools**: Mute, kick, ban users
10. **Analytics**: Track room activity and usage metrics
