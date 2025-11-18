# WebSocket Chatroom - Claude AI Context

This document provides context for Claude AI assistant when working with this project.

## Project Overview

**WebSocket Chatroom** is a lightweight, production-ready WebSocket chat server designed for real-time communication between multiple clients. The project emphasizes:

- **Minimal dependencies** - Only uses the `ws` WebSocket library
- **Modular design** - Easy to import into other Node.js projects
- **Production-ready** - Comprehensive error handling and graceful shutdown
- **Developer-friendly** - Well-documented API with JSDoc comments

## Project Structure

```
websocket-chatroom/
├── src/
│   └── ChatServer.js          # Main chat server class (core module)
├── examples/
│   ├── server.js              # Example server implementation
│   └── client.html            # HTML test client with UI
├── package.json               # Node.js dependencies
├── README.md                  # User-facing documentation
└── CLAUDE.md                  # This file - AI context
```

## Core Architecture

### ChatServer Class (`src/ChatServer.js`)

The `ChatServer` class is the heart of the application. It provides:

**Key Features:**
- Client connection management with auto-incrementing IDs
- Message broadcasting (all clients or selective)
- Event-driven architecture with custom handlers
- Configurable logging
- Promise-based shutdown
- WebSocket state validation

**Design Patterns:**
- **Observer Pattern**: Event handlers for connect/disconnect/message/error
- **Singleton-like behavior**: One server instance per port
- **Dependency Injection**: Configuration options passed via constructor

**Important Implementation Details:**

1. **Client Storage**: Uses `Map<clientId, clientInfo>` for O(1) lookups
   - clientInfo contains: `{ id, ws, connectedAt, ip }`

2. **WebSocket State Checking**: Uses `WebSocket.OPEN` constant (not magic numbers)
   - Prevents sending messages to closed connections

3. **Error Handling**: Try-catch blocks around:
   - Message parsing (invalid JSON)
   - Event handler execution (prevents handler errors from crashing server)
   - Send operations (handles closed connections gracefully)

4. **Message Types**: Exported constants via `MessageTypes` object
   - WELCOME, USER_JOINED, USER_LEFT, MESSAGE, ERROR, DISCONNECT, PRIVATE_MESSAGE

5. **Logging**: Configurable via `options.logging` (default: true)
   - Uses `_log()` and `_logError()` private methods
   - Can be disabled for silent operation in production

### Message Flow

```
Client connects →
  _handleConnection() →
    Assigns clientId →
    Sends WELCOME message →
    Broadcasts USER_JOINED

Client sends message →
  _handleMessage() →
    Parses JSON →
    Calls onMessage handler (if set) →
    Broadcasts to all clients (unless handler returns true)

Client disconnects →
  _handleDisconnect() →
    Removes from clients Map →
    Broadcasts USER_LEFT
```

## API Reference (Quick)

### Constructor Options
```javascript
{
  port: number,              // Server port (default: 8080)
  logging: boolean,          // Enable logging (default: true)
  onClientConnect: fn,       // (clientId, clientInfo) => void
  onClientDisconnect: fn,    // (clientId, clientInfo) => void
  onMessage: fn,             // (clientId, message) => boolean
  onError: fn                // (error, clientId?) => void
}
```

### Public Methods

- `start()` → `ChatServer` - Start server
- `stop()` → `Promise<void>` - Stop server (async)
- `sendToClient(clientId, message)` → `boolean` - Send to one client
- `broadcast(message, excludeId?)` → `number` - Broadcast to all/most
- `sendToClients(clientIds[], message)` → `number` - Send to specific clients
- `getClients()` → `Array<{id, connectedAt, ip}>` - Get client info
- `getClientCount()` → `number` - Get count
- `isClientConnected(clientId)` → `boolean` - Check connection status
- `disconnectClient(clientId, reason?)` → `boolean` - Kick client

### Private Methods (for context)

- `_handleConnection(ws, req)` - Setup new client
- `_handleMessage(clientId, data)` - Process incoming message
- `_handleDisconnect(clientId)` - Cleanup on disconnect
- `_handleServerError(error)` - Server-level error handling
- `_isWebSocketOpen(ws)` - Check WebSocket state
- `_log(...args)` - Conditional logging
- `_logError(...args)` - Conditional error logging

## Code Refactoring Done

The following refactoring was applied to improve code quality:

1. **Replaced magic numbers** with `WebSocket.OPEN` constant
2. **Fixed sendToClients bug** - removed double JSON stringification
3. **Added configurable logging** - can now disable console output
4. **Exported MessageTypes** - constants for message types
5. **Made stop() async** - returns Promise for proper shutdown handling
6. **Added isClientConnected()** - new utility method
7. **Enhanced error handling** - try-catch around all event handlers
8. **Improved JSDoc** - comprehensive documentation for all public methods
9. **Better state validation** - check if server is already running
10. **Cleaner code** - used Array.from() for transformations

## Common Tasks

### Adding a New Feature

1. If it's a new message type, add to `MessageTypes` constant
2. Add method to `ChatServer` class
3. Update JSDoc documentation
4. Add example usage in `examples/server.js`
5. Update README.md
6. Test with `examples/client.html`

### Testing

**Manual Testing:**
```bash
# Start server
npm start

# Open examples/client.html in multiple browser tabs
# Send messages between tabs to test broadcasting
```

**Code Validation:**
```bash
# Check syntax
node --check src/ChatServer.js
node --check examples/server.js

# Test import
node -e "import('./src/ChatServer.js').then(() => console.log('OK'))"
```

### Debugging

Enable verbose logging:
```javascript
const server = new ChatServer({
  port: 8080,
  logging: true  // Already default
});
```

Disable logging for production:
```javascript
const server = new ChatServer({
  port: 8080,
  logging: false
});
```

## Extension Points

The server is designed to be extended. Common extension patterns:

### 1. Custom Message Routing
```javascript
onMessage: (clientId, message) => {
  if (message.type === 'custom') {
    // Handle custom logic
    return true; // Prevent default broadcast
  }
  return false; // Allow default
}
```

### 2. Authentication
```javascript
onClientConnect: (clientId, clientInfo) => {
  // Validate client
  if (!isAuthorized(clientInfo)) {
    chatServer.disconnectClient(clientId, 'Unauthorized');
  }
}
```

### 3. Message Filtering
```javascript
onMessage: (clientId, message) => {
  if (containsProfanity(message.text)) {
    chatServer.sendToClient(clientId, {
      type: MessageTypes.ERROR,
      message: 'Message blocked'
    });
    return true;
  }
  return false;
}
```

### 4. Room/Channel System
```javascript
// Extend ChatServer with rooms
const rooms = new Map(); // roomId -> Set of clientIds

// Broadcast to room only
function broadcastToRoom(roomId, message) {
  const clientIds = rooms.get(roomId);
  if (clientIds) {
    chatServer.sendToClients(Array.from(clientIds), message);
  }
}
```

## Integration Patterns

### With Express.js
```javascript
import express from 'express';
import ChatServer from 'websocket-chatroom';

const app = express();
const chatServer = new ChatServer({ port: 8080 });

chatServer.start();

app.post('/api/broadcast', (req, res) => {
  const sent = chatServer.broadcast(req.body);
  res.json({ sent });
});

app.listen(3000);
```

### As a Module
```javascript
// In another project's package.json
{
  "dependencies": {
    "websocket-chatroom": "file:../websocket-chatroom"
  }
}

// Then import
import ChatServer from 'websocket-chatroom';
```

## Performance Considerations

- **Client Limit**: The server can handle thousands of concurrent connections (limited by OS file descriptors)
- **Message Size**: No built-in size limit, but consider adding validation for large messages
- **Memory**: Each client uses ~1-2KB (clientInfo object + WebSocket overhead)
- **Broadcast Performance**: O(n) where n = number of clients (forEach iteration)

## Security Considerations

Current implementation does NOT include:
- Authentication
- Rate limiting
- Message size limits
- IP-based connection limits
- SSL/TLS (use reverse proxy like nginx)

For production use, consider adding:
1. Authentication via tokens
2. Rate limiting per client
3. Message validation and sanitization
4. WSS (secure WebSocket) support
5. CORS configuration
6. DDoS protection

## Dependencies

**Production:**
- `ws` (^8.14.2) - WebSocket library

**Development:**
- None (pure Node.js)

## Node.js Version

- Minimum: Node.js 14.x (for ES6 modules)
- Recommended: Node.js 18.x or higher

## Known Issues / Limitations

1. **No persistence**: Messages are not stored (in-memory only)
2. **No reconnection logic**: Clients must implement their own reconnection
3. **Single server instance**: No built-in clustering or load balancing
4. **No message history**: New clients don't see previous messages

These are intentional design choices to keep the library lightweight. Implement as needed for your use case.

## Future Enhancement Ideas

- [ ] Add TypeScript definitions (.d.ts file)
- [ ] Add room/channel support
- [ ] Add message history (optional, in-memory)
- [ ] Add rate limiting
- [ ] Add authentication support
- [ ] Add metrics/stats collection
- [ ] Add WebSocket compression support
- [ ] Add cluster mode support

## Contributing Guidelines

When modifying this project:

1. **Keep it lightweight** - Avoid adding heavy dependencies
2. **Document everything** - Update JSDoc and this file
3. **Test thoroughly** - Manual testing with client.html
4. **Maintain backward compatibility** - Don't break existing API
5. **Follow the pattern** - Event-driven, error handling in all methods

## Questions to Ask Before Changes

- Does this belong in the core library or should it be an extension?
- Does this add new dependencies? (Try to avoid)
- Does this break existing APIs?
- Is it well-documented?
- Can it be configured/disabled?

## Git Workflow

- Main development: Work on feature branches prefixed with `claude/`
- Example: `claude/lightweight-chat-server-01CvANfcrALJEnNTgLBGZYYv`
- Commit messages: Use descriptive multi-line commits
- Push: Always push to the branch specified in the session context

---

**Last Updated**: 2025-11-18
**Project Version**: 1.0.0
**Maintainer**: Claude AI Assistant
