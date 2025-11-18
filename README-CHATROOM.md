# WebSocket Chatroom System

A real-time multi-channel chatroom system built with Spring Boot WebSocket technology. This system enables multiple clients to communicate through dedicated chat rooms, direct messages, and broadcasts.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Features](#features)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Testing](#testing)
- [Architecture](#architecture)
- [Security Notes](#security-notes)

## Overview

The chatroom system provides real-time bidirectional communication between clients using WebSocket connections. Clients can:

- Join multiple chat rooms simultaneously
- Send messages to specific rooms
- Send direct messages to other clients
- Broadcast messages to all connected clients
- Create public or private (password-protected) rooms
- Track room membership in real-time

## Quick Start

### 1. Start the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### 2. Access the Test Client

Open your browser and navigate to:
```
http://localhost:8080/chatroom-test
```

### 3. Connect and Chat

1. Enter a client ID (or use the auto-generated one)
2. Click "Connect"
3. Create a new room or join an existing one
4. Start chatting!

## How It Works

### Connection Flow

```
┌─────────┐                                    ┌─────────┐
│ Client  │                                    │ Server  │
└────┬────┘                                    └────┬────┘
     │                                              │
     │  1. WebSocket Handshake                     │
     │  ws://server/ws/chat?client=user123         │
     ├────────────────────────────────────────────►│
     │                                              │
     │  2. Connection Established                  │
     │◄────────────────────────────────────────────┤
     │  {"type":"connected","data":{"clientId":..}}│
     │                                              │
     │  3. Join Room                               │
     │  {"type":"join_room","room":"general"}      │
     ├────────────────────────────────────────────►│
     │                                              │
     │  4. Room Joined Confirmation                │
     │◄────────────────────────────────────────────┤
     │  {"type":"room_joined","data":{"room":..}}  │
     │                                              │
     │  5. Send Message                            │
     │  {"type":"room_message","room":"general",   │
     │   "data":"Hello!"}                          │
     ├────────────────────────────────────────────►│
     │                                              │
     │  6. Message Broadcast to Room Members       │
     │◄────────────────────────────────────────────┤
     │  {"type":"room_message","data":"Hello!",    │
     │   "from":"user123"}                         │
     │                                              │
```

### Room Management

**Rooms are stored in the database** and have the following properties:

- **Room ID**: Unique identifier (e.g., "general", "tech-talk")
- **Name**: Display name (e.g., "General Chat")
- **Type**: PUBLIC or PRIVATE
- **Password**: Optional password for private rooms
- **Persistent**: If true, room survives when empty; if false, auto-deleted

**In-Memory State:**
- Active connections and room memberships are tracked in memory
- When a client disconnects, they're automatically removed from all rooms
- Non-persistent empty rooms are deleted automatically

## Features

### 1. Multi-Room Support

Clients can join multiple rooms and participate in different conversations:

```javascript
// Join first room
ws.send(JSON.stringify({
  type: "join_room",
  room: "general"
}));

// Join second room
ws.send(JSON.stringify({
  type: "join_room",
  room: "tech-talk"
}));

// Send message to specific room
ws.send(JSON.stringify({
  type: "room_message",
  room: "general",
  data: "Hello everyone in general!"
}));
```

### 2. Private Rooms

Create password-protected rooms for private conversations:

```bash
# Create private room via API
curl -X POST http://localhost:8080/api/v1/rooms/create \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": "secret-room",
    "name": "Secret Chat",
    "type": "PRIVATE",
    "password": "mypassword123",
    "createdBy": "admin",
    "persistent": true
  }'
```

```javascript
// Join with password
ws.send(JSON.stringify({
  type: "join_room",
  room: "secret-room",
  password: "mypassword123"
}));
```

### 3. Direct Messaging

Send private messages to specific clients:

```javascript
ws.send(JSON.stringify({
  type: "direct_message",
  to: "user456",
  data: "Hey, want to chat privately?"
}));
```

### 4. Broadcasting

Send messages to all connected clients (regardless of rooms):

```javascript
ws.send(JSON.stringify({
  type: "broadcast",
  data: "Server maintenance in 5 minutes!"
}));
```

### 5. Member Tracking

See who's in each room in real-time:

```javascript
// Request room members
ws.send(JSON.stringify({
  type: "get_room_members",
  room: "general"
}));

// Response
{
  "type": "room_members",
  "success": true,
  "data": {
    "room": "general",
    "members": ["user123", "user456", "user789"]
  }
}
```

## Usage Examples

### Example 1: Simple Chat Client (JavaScript)

```javascript
// Connect to WebSocket
const clientId = "user_" + Date.now();
const ws = new WebSocket(`ws://localhost:8080/ws/chat?client=${clientId}`);

ws.onopen = () => {
  console.log("Connected!");

  // Join a room
  ws.send(JSON.stringify({
    type: "join_room",
    room: "general"
  }));
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log("Received:", message);

  if (message.type === "room_message") {
    console.log(`${message.data.from}: ${message.message}`);
  }
};

// Send a message
function sendMessage(text) {
  ws.send(JSON.stringify({
    type: "room_message",
    room: "general",
    data: text
  }));
}

sendMessage("Hello, world!");
```

### Example 2: Create Room via REST API

```bash
# Create a public room
curl -X POST http://localhost:8080/api/v1/rooms/create \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": "gaming",
    "name": "Gaming Chat",
    "description": "Discuss your favorite games",
    "type": "PUBLIC",
    "createdBy": "admin",
    "persistent": true
  }'

# List all rooms
curl http://localhost:8080/api/v1/rooms/list

# Get room details
curl http://localhost:8080/api/v1/rooms/gaming

# Get room members
curl http://localhost:8080/api/v1/rooms/gaming/members
```

### Example 3: Node.js Client

```javascript
const WebSocket = require('ws');

const clientId = process.argv[2] || 'node_client';
const ws = new WebSocket(`ws://localhost:8080/ws/chat?client=${clientId}`);

ws.on('open', () => {
  console.log(`Connected as ${clientId}`);

  // Join room
  ws.send(JSON.stringify({
    type: 'join_room',
    room: 'general'
  }));

  // Send a message after 1 second
  setTimeout(() => {
    ws.send(JSON.stringify({
      type: 'room_message',
      room: 'general',
      data: 'Hello from Node.js!'
    }));
  }, 1000);
});

ws.on('message', (data) => {
  const message = JSON.parse(data);

  switch(message.type) {
    case 'room_joined':
      console.log(`Joined room: ${message.data.room}`);
      break;
    case 'room_message':
      console.log(`[${message.data.room}] ${message.data.from}: ${message.message}`);
      break;
    case 'member_joined':
      console.log(`${message.data.member} joined ${message.data.room}`);
      break;
  }
});

ws.on('error', (error) => {
  console.error('WebSocket error:', error);
});
```

### Example 4: Python Client

```python
import asyncio
import websockets
import json
import sys

async def chat_client(client_id):
    uri = f"ws://localhost:8080/ws/chat?client={client_id}"

    async with websockets.connect(uri) as websocket:
        print(f"Connected as {client_id}")

        # Join room
        await websocket.send(json.dumps({
            "type": "join_room",
            "room": "general"
        }))

        # Listen for messages
        async def receive():
            async for message in websocket:
                data = json.loads(message)
                if data["type"] == "room_message":
                    print(f"[{data['data']['room']}] {data['data']['from']}: {data['message']}")
                elif data["type"] == "room_joined":
                    print(f"Joined room: {data['data']['room']}")

        # Send messages
        async def send():
            await asyncio.sleep(1)
            await websocket.send(json.dumps({
                "type": "room_message",
                "room": "general",
                "data": "Hello from Python!"
            }))

        await asyncio.gather(receive(), send())

if __name__ == "__main__":
    client_id = sys.argv[1] if len(sys.argv) > 1 else "python_client"
    asyncio.run(chat_client(client_id))
```

## API Reference

### WebSocket Endpoint

**URL:** `ws://localhost:8080/ws/chat`

**Query Parameters:**
- `client` (required): Unique client identifier
- `room` (optional): Auto-join this room on connection

**Example:**
```
ws://localhost:8080/ws/chat?client=user123&room=general
```

### Message Types (Client → Server)

#### 1. Join Room
```json
{
  "type": "join_room",
  "room": "room-id",
  "password": "optional-password"
}
```

#### 2. Leave Room
```json
{
  "type": "leave_room",
  "room": "room-id"
}
```

#### 3. Room Message
```json
{
  "type": "room_message",
  "room": "room-id",
  "data": "Your message here"
}
```

#### 4. Direct Message
```json
{
  "type": "direct_message",
  "to": "target-client-id",
  "data": "Your private message"
}
```

#### 5. Broadcast Message
```json
{
  "type": "broadcast",
  "data": "Message to everyone"
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
  "room": "room-id"
}
```

### Response Messages (Server → Client)

#### Connected
```json
{
  "type": "connected",
  "success": true,
  "message": "Connected to chat server",
  "data": {
    "clientId": "user123"
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
    "members": ["user123", "user456"]
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
    "from": "user123",
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
    "member": "user789"
  }
}
```

#### Error
```json
{
  "type": "error",
  "success": false,
  "message": "Failed to join room: invalid password"
}
```

### REST API Endpoints

#### Create Room
```http
POST /api/v1/rooms/create
Content-Type: application/json

{
  "roomId": "my-room",
  "name": "My Room",
  "description": "Room description",
  "type": "PUBLIC",
  "password": null,
  "createdBy": "user123",
  "persistent": true
}
```

**Response:**
```json
{
  "success": true,
  "message": "Room created successfully",
  "room": {
    "id": 1,
    "roomId": "my-room",
    "name": "My Room",
    "type": "PUBLIC",
    "persistent": true
  }
}
```

#### List Rooms
```http
GET /api/v1/rooms/list
```

**Response:**
```json
{
  "success": true,
  "rooms": [
    {
      "roomId": "general",
      "name": "General Chat",
      "description": "General discussion",
      "type": "PUBLIC",
      "memberCount": 5,
      "persistent": true,
      "createdBy": "admin",
      "createdAt": "2024-11-18T10:30:00"
    }
  ]
}
```

#### Get Room Details
```http
GET /api/v1/rooms/{roomId}
```

#### Get Room Members
```http
GET /api/v1/rooms/{roomId}/members
```

**Response:**
```json
{
  "success": true,
  "roomId": "general",
  "members": ["user123", "user456", "user789"]
}
```

#### Delete Room
```http
DELETE /api/v1/rooms/{roomId}
```

## Testing

### Interactive Test Client

The easiest way to test is using the built-in web client:

1. Start the application: `./gradlew bootRun`
2. Open browser: `http://localhost:8080/chatroom-test`
3. Open multiple browser tabs to simulate multiple users
4. Create rooms and start chatting!

**Test Client Features:**
- Connect/disconnect with custom client ID
- Create new rooms
- Join/leave rooms
- Send room messages, direct messages, and broadcasts
- View room members in real-time
- Clear message history

### Testing with wscat

Install wscat globally:
```bash
npm install -g wscat
```

Connect and test:
```bash
# Connect
wscat -c "ws://localhost:8080/ws/chat?client=test_user"

# Then type messages:
{"type":"join_room","room":"general"}
{"type":"room_message","room":"general","data":"Hello!"}
{"type":"list_rooms"}
{"type":"leave_room","room":"general"}
```

### Testing with curl

```bash
# Create a room
curl -X POST http://localhost:8080/api/v1/rooms/create \
  -H "Content-Type: application/json" \
  -d '{"roomId":"test","name":"Test Room","type":"PUBLIC","createdBy":"admin","persistent":true}'

# List rooms
curl http://localhost:8080/api/v1/rooms/list

# Get room members
curl http://localhost:8080/api/v1/rooms/test/members

# Delete room
curl -X DELETE http://localhost:8080/api/v1/rooms/test
```

### Multi-Client Testing Script

```bash
# Terminal 1
wscat -c "ws://localhost:8080/ws/chat?client=alice"
{"type":"join_room","room":"general"}
{"type":"room_message","room":"general","data":"Hi, I'm Alice!"}

# Terminal 2
wscat -c "ws://localhost:8080/ws/chat?client=bob"
{"type":"join_room","room":"general"}
{"type":"room_message","room":"general","data":"Hey Alice, I'm Bob!"}

# Terminal 3 - Observer
wscat -c "ws://localhost:8080/ws/chat?client=observer"
{"type":"join_room","room":"general"}
```

## Architecture

### System Components

```
┌─────────────────────────────────────────────────────────┐
│                     WebSocket Layer                      │
├─────────────────────────────────────────────────────────┤
│  ChatWebSocketHandler                                    │
│  - Connection management                                 │
│  - Message parsing & routing                             │
│  - Auto-join rooms via URL                              │
└─────────────────┬───────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│                     Service Layer                        │
├─────────────────────────────────────────────────────────┤
│  RoomManager                                             │
│  - Session tracking (in-memory)                          │
│  - Room membership management                            │
│  - Message broadcasting                                  │
│  - Auto-cleanup empty rooms                             │
└─────────────────┬───────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│                   Persistence Layer                      │
├─────────────────────────────────────────────────────────┤
│  RoomRepository (JPA)                                    │
│  Room Entity                                             │
│  - Room metadata storage                                 │
│  - H2 in-memory database                                │
└─────────────────────────────────────────────────────────┘
```

### Data Flow

**Message Sent:**
```
Client → WebSocket → ChatWebSocketHandler → RoomManager → Find Room Members → Broadcast to Sessions
```

**Join Room:**
```
Client → WebSocket → ChatWebSocketHandler → RoomManager → Verify Room → Add to Membership → Confirm
```

**Auto-Cleanup:**
```
Client Disconnect → RoomManager → Remove from All Rooms → Check Room Empty → Delete if Non-Persistent
```

### State Management

**In-Memory (ConcurrentHashMap):**
- Active WebSocket sessions
- Room memberships (which clients are in which rooms)
- Client-to-session mappings

**Database (H2):**
- Room metadata (name, type, password, etc.)
- Room persistence settings
- Creation timestamps

## Security Notes

### Current Implementation (Development Mode)

⚠️ **WARNING:** The current implementation is designed for development and testing. Do NOT use in production without security hardening!

**Current Security Issues:**
- All endpoints are publicly accessible (no authentication)
- Client IDs are self-assigned (easily spoofed)
- CSRF protection is disabled
- WebSocket origin checking uses wildcard `*`
- No rate limiting
- No input sanitization
- Passwords stored in plain text

### Production Security Recommendations

#### 1. Enable Authentication

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    // Verify JWT token or session
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
        return;
    }

    String userId = auth.getName(); // Use authenticated user ID
    roomManager.registerSession(session, userId);
}
```

#### 2. Restrict WebSocket Origins

```java
@Override
public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(chatWebSocketHandler, "/ws/chat")
            .setAllowedOrigins(
                "https://yourdomain.com",
                "https://app.yourdomain.com"
            );
}
```

#### 3. Add Rate Limiting

```java
private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String clientId = roomManager.getClientId(session);
    RateLimiter limiter = rateLimiters.computeIfAbsent(
        clientId,
        k -> RateLimiter.create(10.0) // 10 messages per second
    );

    if (!limiter.tryAcquire()) {
        sendError(session, "Rate limit exceeded");
        return;
    }

    // Process message...
}
```

#### 4. Sanitize Input

```java
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

private final PolicyFactory sanitizer = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

private String sanitizeInput(String input) {
    return sanitizer.sanitize(input);
}
```

#### 5. Hash Passwords

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

// When creating room
room.setPassword(passwordEncoder.encode(rawPassword));

// When joining
if (!passwordEncoder.matches(providedPassword, room.getPassword())) {
    return false;
}
```

#### 6. Add Authorization

```java
// Room-level permissions
public enum RoomPermission {
    READ, WRITE, ADMIN
}

// Check before allowing message send
if (!hasPermission(clientId, roomId, RoomPermission.WRITE)) {
    sendError(session, "You don't have permission to post in this room");
    return;
}
```

#### 7. Use WSS (WebSocket Secure)

Configure TLS/SSL in production:
```
wss://yourdomain.com/ws/chat
```

## Advanced Topics

### Scaling to Multiple Servers

For horizontal scaling, you'll need distributed state management:

1. **Use Redis for shared state:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

2. **Use message broker (RabbitMQ/Kafka):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

3. **Use Spring Session for distributed sessions**

### Adding Message History

1. Create Message entity
2. Store messages in database when sent
3. Add endpoint to retrieve message history
4. Send history when client joins room

### Adding Presence System

Track online/offline status:
```java
public enum UserStatus {
    ONLINE, AWAY, BUSY, OFFLINE
}

public void updateStatus(String clientId, UserStatus status) {
    // Store status in Redis
    // Broadcast to relevant rooms
}
```

## Troubleshooting

### WebSocket Connection Fails

**Problem:** Browser can't connect to WebSocket

**Solutions:**
1. Check if application is running: `curl http://localhost:8080/chatroom-test`
2. Verify WebSocket URL: `ws://localhost:8080/ws/chat?client=test`
3. Check browser console for errors
4. Ensure firewall allows WebSocket connections
5. Try disabling browser extensions

### Messages Not Received

**Problem:** Sent messages don't appear

**Solutions:**
1. Verify client is in the room: `{"type":"get_room_members","room":"general"}`
2. Check browser console for JSON parsing errors
3. Verify message format matches protocol
4. Check server logs for errors

### Room Not Found

**Problem:** Can't join room

**Solutions:**
1. List all rooms: `curl http://localhost:8080/api/v1/rooms/list`
2. Create room first via API or test client
3. Check room ID spelling (case-sensitive)
4. Verify room wasn't auto-deleted (if non-persistent and empty)

### High Memory Usage

**Problem:** Application uses too much memory

**Solutions:**
1. Limit concurrent connections
2. Add message size limits
3. Implement session timeout
4. Use connection pooling
5. Consider distributed state (Redis)

## Further Documentation

- **Architecture Details:** See `docs/chatroom-architecture.md`
- **Spring WebSocket Guide:** https://spring.io/guides/gs/messaging-stomp-websocket/
- **WebSocket Protocol:** https://datatracker.ietf.org/doc/html/rfc6455

## License

This chatroom system is part of the Magic Link Service project.

---

**Questions or Issues?** Open an issue on GitHub or check the comprehensive architecture documentation in `docs/chatroom-architecture.md`.
