import { WebSocketServer, WebSocket } from 'ws';

/**
 * Message type constants
 */
export const MessageTypes = {
  WELCOME: 'welcome',
  USER_JOINED: 'user-joined',
  USER_LEFT: 'user-left',
  MESSAGE: 'message',
  ERROR: 'error',
  DISCONNECT: 'disconnect',
  PRIVATE_MESSAGE: 'private-message'
};

/**
 * Lightweight WebSocket Chat Server
 * Handles multiple client connections and message broadcasting
 */
class ChatServer {
  /**
   * Create a new ChatServer instance
   * @param {Object} options - Configuration options
   * @param {number} options.port - Server port (default: 8080)
   * @param {boolean} options.logging - Enable/disable logging (default: true)
   * @param {Function} options.onClientConnect - Called when client connects
   * @param {Function} options.onClientDisconnect - Called when client disconnects
   * @param {Function} options.onMessage - Called when message received (return true to prevent default broadcast)
   * @param {Function} options.onError - Called on error
   */
  constructor(options = {}) {
    this.port = options.port || 8080;
    this.logging = options.logging !== false; // Default to true
    this.clients = new Map(); // Map to store client connections with metadata
    this.wss = null;
    this.clientIdCounter = 0;

    // Event handlers (can be overridden)
    this.onClientConnect = options.onClientConnect || null;
    this.onClientDisconnect = options.onClientDisconnect || null;
    this.onMessage = options.onMessage || null;
    this.onError = options.onError || null;
  }

  /**
   * Log message if logging is enabled
   * @private
   */
  _log(...args) {
    if (this.logging) {
      console.log(...args);
    }
  }

  /**
   * Log error if logging is enabled
   * @private
   */
  _logError(...args) {
    if (this.logging) {
      console.error(...args);
    }
  }

  /**
   * Start the WebSocket server
   * @returns {ChatServer} The server instance for chaining
   */
  start() {
    if (this.wss) {
      this._logError('Server is already running');
      return this;
    }

    try {
      this.wss = new WebSocketServer({ port: this.port });

      this.wss.on('connection', (ws, req) => {
        this._handleConnection(ws, req);
      });

      this.wss.on('error', (error) => {
        this._handleServerError(error);
      });

      this._log(`Chat server started on port ${this.port}`);
      return this;
    } catch (error) {
      this._logError('Failed to start server:', error);
      this._handleServerError(error);
      throw error;
    }
  }

  /**
   * Handle server-level errors
   * @private
   */
  _handleServerError(error) {
    if (this.onError) {
      this.onError(error);
    } else {
      this._logError('WebSocket Server Error:', error);
    }
  }

  /**
   * Check if a WebSocket is in OPEN state
   * @private
   */
  _isWebSocketOpen(ws) {
    return ws && ws.readyState === WebSocket.OPEN;
  }

  /**
   * Handle new client connection
   * @private
   */
  _handleConnection(ws, req) {
    const clientId = ++this.clientIdCounter;
    const clientInfo = {
      id: clientId,
      ws: ws,
      connectedAt: new Date(),
      ip: req.socket.remoteAddress
    };

    this.clients.set(clientId, clientInfo);
    this._log(`Client ${clientId} connected. Total clients: ${this.clients.size}`);

    // Notify about new connection
    if (this.onClientConnect) {
      try {
        this.onClientConnect(clientId, clientInfo);
      } catch (error) {
        this._logError(`Error in onClientConnect handler:`, error);
      }
    }

    // Send welcome message to the new client
    this.sendToClient(clientId, {
      type: MessageTypes.WELCOME,
      clientId: clientId,
      message: 'Connected to chat server',
      totalClients: this.clients.size
    });

    // Broadcast to others that a new client joined
    this.broadcast({
      type: MessageTypes.USER_JOINED,
      clientId: clientId,
      totalClients: this.clients.size
    }, clientId);

    // Handle incoming messages
    ws.on('message', (data) => {
      this._handleMessage(clientId, data);
    });

    // Handle client disconnect
    ws.on('close', () => {
      this._handleDisconnect(clientId);
    });

    // Handle errors
    ws.on('error', (error) => {
      this._logError(`Client ${clientId} error:`, error);
      if (this.onError) {
        try {
          this.onError(error, clientId);
        } catch (handlerError) {
          this._logError(`Error in onError handler:`, handlerError);
        }
      }
    });
  }

  /**
   * Handle incoming messages from clients
   * @private
   */
  _handleMessage(clientId, data) {
    try {
      const message = JSON.parse(data.toString());

      this._log(`Message from client ${clientId}:`, message);

      // Custom message handler
      if (this.onMessage) {
        try {
          const handled = this.onMessage(clientId, message);
          if (handled) return; // If custom handler returns true, don't process further
        } catch (error) {
          this._logError(`Error in onMessage handler:`, error);
          this.sendToClient(clientId, {
            type: MessageTypes.ERROR,
            message: 'Error processing message'
          });
          return;
        }
      }

      // Default message handling - broadcast to all clients
      this.broadcast({
        type: MessageTypes.MESSAGE,
        clientId: clientId,
        data: message,
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      this._logError(`Error parsing message from client ${clientId}:`, error);
      this.sendToClient(clientId, {
        type: MessageTypes.ERROR,
        message: 'Invalid message format. Please send valid JSON.'
      });
    }
  }

  /**
   * Handle client disconnect
   * @private
   */
  _handleDisconnect(clientId) {
    const clientInfo = this.clients.get(clientId);
    if (clientInfo) {
      this.clients.delete(clientId);
      this._log(`Client ${clientId} disconnected. Total clients: ${this.clients.size}`);

      if (this.onClientDisconnect) {
        try {
          this.onClientDisconnect(clientId, clientInfo);
        } catch (error) {
          this._logError(`Error in onClientDisconnect handler:`, error);
        }
      }

      // Notify other clients
      this.broadcast({
        type: MessageTypes.USER_LEFT,
        clientId: clientId,
        totalClients: this.clients.size
      });
    }
  }

  /**
   * Send message to a specific client
   * @param {number} clientId - The client ID
   * @param {Object} message - The message object to send
   * @returns {boolean} True if message was sent successfully
   */
  sendToClient(clientId, message) {
    const client = this.clients.get(clientId);
    if (client && this._isWebSocketOpen(client.ws)) {
      try {
        client.ws.send(JSON.stringify(message));
        return true;
      } catch (error) {
        this._logError(`Error sending message to client ${clientId}:`, error);
        return false;
      }
    }
    return false;
  }

  /**
   * Broadcast message to all clients (or all except one)
   * @param {Object} message - The message object to broadcast
   * @param {number|null} excludeClientId - Optional client ID to exclude from broadcast
   * @returns {number} Number of clients that received the message
   */
  broadcast(message, excludeClientId = null) {
    const messageStr = JSON.stringify(message);
    let sentCount = 0;

    this.clients.forEach((client, clientId) => {
      if (excludeClientId !== clientId && this._isWebSocketOpen(client.ws)) {
        try {
          client.ws.send(messageStr);
          sentCount++;
        } catch (error) {
          this._logError(`Error broadcasting to client ${clientId}:`, error);
        }
      }
    });

    return sentCount;
  }

  /**
   * Send message to specific clients by their IDs
   * @param {number[]} clientIds - Array of client IDs
   * @param {Object} message - The message object to send
   * @returns {number} Number of clients that received the message
   */
  sendToClients(clientIds, message) {
    let sentCount = 0;

    clientIds.forEach(clientId => {
      if (this.sendToClient(clientId, message)) {
        sentCount++;
      }
    });

    return sentCount;
  }

  /**
   * Get information about connected clients
   * @returns {Array<Object>} Array of client information objects
   */
  getClients() {
    return Array.from(this.clients.values()).map(client => ({
      id: client.id,
      connectedAt: client.connectedAt,
      ip: client.ip
    }));
  }

  /**
   * Get client count
   * @returns {number} Number of connected clients
   */
  getClientCount() {
    return this.clients.size;
  }

  /**
   * Check if a client is connected
   * @param {number} clientId - The client ID to check
   * @returns {boolean} True if client is connected
   */
  isClientConnected(clientId) {
    const client = this.clients.get(clientId);
    return client && this._isWebSocketOpen(client.ws);
  }

  /**
   * Disconnect a specific client
   * @param {number} clientId - The client ID to disconnect
   * @param {string} reason - Reason for disconnection
   * @returns {boolean} True if client was disconnected successfully
   */
  disconnectClient(clientId, reason = 'Disconnected by server') {
    const client = this.clients.get(clientId);
    if (client) {
      this.sendToClient(clientId, {
        type: MessageTypes.DISCONNECT,
        reason: reason
      });

      try {
        client.ws.close();
        return true;
      } catch (error) {
        this._logError(`Error disconnecting client ${clientId}:`, error);
        return false;
      }
    }
    return false;
  }

  /**
   * Stop the server and disconnect all clients
   * @returns {Promise<void>} Promise that resolves when server is stopped
   */
  stop() {
    return new Promise((resolve, reject) => {
      if (!this.wss) {
        this._log('Server is not running');
        resolve();
        return;
      }

      try {
        // Disconnect all clients
        const clientIds = Array.from(this.clients.keys());
        clientIds.forEach(clientId => {
          this.disconnectClient(clientId, 'Server shutting down');
        });

        this.wss.close((error) => {
          if (error) {
            this._logError('Error stopping server:', error);
            reject(error);
          } else {
            this._log('Chat server stopped');
            this.wss = null;
            resolve();
          }
        });
      } catch (error) {
        this._logError('Error during server shutdown:', error);
        reject(error);
      }
    });
  }
}

export default ChatServer;
