import ChatServer, { MessageTypes } from '../src/ChatServer.js';

// Create a chat server instance
const chatServer = new ChatServer({
  port: 8080,
  logging: true, // Enable logging (default: true)

  // Optional: Custom event handlers
  onClientConnect: (clientId, clientInfo) => {
    console.log(`[Custom Handler] Client ${clientId} connected from ${clientInfo.ip}`);
  },

  onClientDisconnect: (clientId, clientInfo) => {
    console.log(`[Custom Handler] Client ${clientId} disconnected`);
  },

  onMessage: (clientId, message) => {
    console.log(`[Custom Handler] Received from client ${clientId}:`, message);

    // Example: Handle custom message types
    if (message.type === 'private') {
      // Send private message to specific client
      const targetId = message.targetId;
      if (targetId) {
        chatServer.sendToClient(targetId, {
          type: MessageTypes.PRIVATE_MESSAGE,
          from: clientId,
          message: message.text,
          timestamp: new Date().toISOString()
        });

        // Confirm to sender
        chatServer.sendToClient(clientId, {
          type: 'private-message-sent',
          to: targetId,
          timestamp: new Date().toISOString()
        });

        return true; // Prevent default broadcast
      }
    }

    // Return false to allow default broadcasting
    return false;
  },

  onError: (error, clientId) => {
    console.error(`[Custom Handler] Error ${clientId ? `from client ${clientId}` : 'on server'}:`, error);
  }
});

// Start the server
chatServer.start();

// Example: Log client count every 30 seconds
setInterval(() => {
  const count = chatServer.getClientCount();
  if (count > 0) {
    console.log(`Active clients: ${count}`);
  }
}, 30000);

// Graceful shutdown
const gracefulShutdown = async () => {
  console.log('\nShutting down server...');
  try {
    await chatServer.stop();
    console.log('Server stopped successfully');
    process.exit(0);
  } catch (error) {
    console.error('Error during shutdown:', error);
    process.exit(1);
  }
};

process.on('SIGINT', gracefulShutdown);
process.on('SIGTERM', gracefulShutdown);

console.log('Chat server is running!');
console.log('Press Ctrl+C to stop');
