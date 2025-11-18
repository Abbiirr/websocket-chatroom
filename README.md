# WebSocket Chatroom

A **lightweight**, **modular** WebSocket chat server for real-time communication between multiple clients. Designed to be easily imported and integrated into other Node.js projects.

## Features

- ✅ Lightweight with minimal dependencies (only `ws` library)
- ✅ Easy to import and integrate into other projects
- ✅ Support for multiple simultaneous clients
- ✅ Message broadcasting to all clients
- ✅ Private messaging between specific clients
- ✅ Client connection/disconnection handling
- ✅ Custom event handlers
- ✅ Client metadata tracking
- ✅ Graceful shutdown support

## Installation

```bash
npm install
```

## Quick Start

### 1. Basic Server

```javascript
import ChatServer from './src/ChatServer.js';

const chatServer = new ChatServer({ port: 8080 });
chatServer.start();
```

### 2. Run the Example Server

```bash
npm start
```

### 3. Open the Test Client

Open `examples/client.html` in multiple browser tabs to test multi-client communication.

## Usage

### Importing into Your Project

You can easily import this chat server into any Node.js project:

```javascript
import ChatServer from 'websocket-chatroom';

const server = new ChatServer({ port: 8080 });
server.start();
```

### API Reference

#### Constructor Options

```javascript
const chatServer = new ChatServer({
  port: 8080,                    // Server port (default: 8080)
  onClientConnect: (clientId, clientInfo) => {},    // Called when client connects
  onClientDisconnect: (clientId, clientInfo) => {}, // Called when client disconnects
  onMessage: (clientId, message) => {},             // Called when message received
  onError: (error, clientId) => {}                  // Called on error
});
```

#### Methods

##### `start()`
Start the WebSocket server.

```javascript
chatServer.start();
```

##### `stop()`
Stop the server and disconnect all clients.

```javascript
chatServer.stop();
```

##### `sendToClient(clientId, message)`
Send a message to a specific client.

```javascript
chatServer.sendToClient(5, {
  type: 'notification',
  text: 'Hello, Client 5!'
});
```

##### `broadcast(message, excludeClientId)`
Broadcast a message to all clients (optionally exclude one).

```javascript
// Broadcast to all
chatServer.broadcast({ type: 'announcement', text: 'Server maintenance in 5 minutes' });

// Broadcast to all except client 3
chatServer.broadcast({ type: 'message', text: 'Hello everyone!' }, 3);
```

##### `sendToClients(clientIds, message)`
Send a message to specific clients.

```javascript
chatServer.sendToClients([1, 3, 5], {
  type: 'group-message',
  text: 'Team meeting in 10 minutes'
});
```

##### `getClients()`
Get information about all connected clients.

```javascript
const clients = chatServer.getClients();
// Returns: [{ id: 1, connectedAt: Date, ip: '127.0.0.1' }, ...]
```

##### `getClientCount()`
Get the number of connected clients.

```javascript
const count = chatServer.getClientCount();
```

##### `disconnectClient(clientId, reason)`
Disconnect a specific client.

```javascript
chatServer.disconnectClient(5, 'Kicked by admin');
```

## Message Format

All messages are sent as JSON. The server expects and sends messages in this format:

### Client to Server

```json
{
  "text": "Hello, world!",
  "type": "chat"
}
```

### Server to Client

The server sends various message types:

#### Welcome Message
```json
{
  "type": "welcome",
  "clientId": 1,
  "message": "Connected to chat server",
  "totalClients": 3
}
```

#### User Joined
```json
{
  "type": "user-joined",
  "clientId": 2,
  "totalClients": 4
}
```

#### User Left
```json
{
  "type": "user-left",
  "clientId": 2,
  "totalClients": 3
}
```

#### Broadcast Message
```json
{
  "type": "message",
  "clientId": 1,
  "data": { "text": "Hello!" },
  "timestamp": "2025-11-18T12:00:00.000Z"
}
```

## Advanced Usage

### Custom Message Handling

You can implement custom message routing and processing:

```javascript
const chatServer = new ChatServer({
  port: 8080,

  onMessage: (clientId, message) => {
    // Handle private messages
    if (message.type === 'private') {
      chatServer.sendToClient(message.targetId, {
        type: 'private-message',
        from: clientId,
        text: message.text
      });
      return true; // Prevent default broadcast
    }

    // Handle commands
    if (message.text?.startsWith('/')) {
      handleCommand(clientId, message.text);
      return true;
    }

    return false; // Allow default broadcast
  }
});
```

### Track Client Metadata

```javascript
const chatServer = new ChatServer({
  onClientConnect: (clientId, clientInfo) => {
    console.log(`New connection:`, {
      id: clientId,
      ip: clientInfo.ip,
      time: clientInfo.connectedAt
    });

    // Send welcome message
    chatServer.sendToClient(clientId, {
      type: 'welcome',
      message: `Welcome, Client ${clientId}!`
    });
  }
});
```

### Integration Example

Import into another project:

```javascript
// app.js in another project
import ChatServer from 'websocket-chatroom';
import express from 'express';

const app = express();
const chatServer = new ChatServer({ port: 8080 });

// Start chat server
chatServer.start();

// Your Express app on different port
app.listen(3000, () => {
  console.log('Web server on port 3000');
  console.log('Chat server on port 8080');
});

// Integrate chat with your app logic
app.post('/api/broadcast', (req, res) => {
  chatServer.broadcast({
    type: 'announcement',
    text: req.body.message
  });
  res.json({ sent: chatServer.getClientCount() });
});
```

## Client Connection (JavaScript)

```javascript
const ws = new WebSocket('ws://localhost:8080');

ws.onopen = () => {
  console.log('Connected');
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Received:', data);
};

// Send a message
ws.send(JSON.stringify({
  text: 'Hello from client!',
  type: 'chat'
}));
```

## File Structure

```
websocket-chatroom/
├── src/
│   └── ChatServer.js      # Main chat server module
├── examples/
│   ├── server.js          # Example server implementation
│   └── client.html        # HTML test client
├── package.json
└── README.md
```

## Requirements

- Node.js >= 14.x
- npm or yarn

## Dependencies

- `ws`: WebSocket library for Node.js

## License

MIT

## Contributing

Feel free to submit issues and pull requests!

## Support

For questions and support, please open an issue on GitHub.
