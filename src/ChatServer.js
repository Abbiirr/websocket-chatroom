import { WebSocketServer } from 'ws';

/**
 * Lightweight WebSocket Chat Server
 * Handles multiple client connections and message broadcasting
 */
class ChatServer {
  constructor(options = {}) {
    this.port = options.port || 8080;
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
   * Start the WebSocket server
   */
  start() {
    this.wss = new WebSocketServer({ port: this.port });

    this.wss.on('connection', (ws, req) => {
      this._handleConnection(ws, req);
    });

    this.wss.on('error', (error) => {
      if (this.onError) {
        this.onError(error);
      } else {
        console.error('WebSocket Server Error:', error);
      }
    });

    console.log(`Chat server started on port ${this.port}`);
    return this;
  }

  /**
   * Handle new client connection
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
    console.log(`Client ${clientId} connected. Total clients: ${this.clients.size}`);

    // Notify about new connection
    if (this.onClientConnect) {
      this.onClientConnect(clientId, clientInfo);
    }

    // Send welcome message to the new client
    this.sendToClient(clientId, {
      type: 'welcome',
      clientId: clientId,
      message: 'Connected to chat server',
      totalClients: this.clients.size
    });

    // Broadcast to others that a new client joined
    this.broadcast({
      type: 'user-joined',
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
      console.error(`Client ${clientId} error:`, error);
      if (this.onError) {
        this.onError(error, clientId);
      }
    });
  }

  /**
   * Handle incoming messages from clients
   */
  _handleMessage(clientId, data) {
    try {
      const message = JSON.parse(data.toString());

      console.log(`Message from client ${clientId}:`, message);

      // Custom message handler
      if (this.onMessage) {
        const handled = this.onMessage(clientId, message);
        if (handled) return; // If custom handler returns true, don't process further
      }

      // Default message handling - broadcast to all clients
      this.broadcast({
        type: 'message',
        clientId: clientId,
        data: message,
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      console.error(`Error parsing message from client ${clientId}:`, error);
      this.sendToClient(clientId, {
        type: 'error',
        message: 'Invalid message format. Please send valid JSON.'
      });
    }
  }

  /**
   * Handle client disconnect
   */
  _handleDisconnect(clientId) {
    const clientInfo = this.clients.get(clientId);
    if (clientInfo) {
      this.clients.delete(clientId);
      console.log(`Client ${clientId} disconnected. Total clients: ${this.clients.size}`);

      if (this.onClientDisconnect) {
        this.onClientDisconnect(clientId, clientInfo);
      }

      // Notify other clients
      this.broadcast({
        type: 'user-left',
        clientId: clientId,
        totalClients: this.clients.size
      });
    }
  }

  /**
   * Send message to a specific client
   */
  sendToClient(clientId, message) {
    const client = this.clients.get(clientId);
    if (client && client.ws.readyState === 1) { // 1 = OPEN
      client.ws.send(JSON.stringify(message));
      return true;
    }
    return false;
  }

  /**
   * Broadcast message to all clients (or all except one)
   */
  broadcast(message, excludeClientId = null) {
    const messageStr = JSON.stringify(message);
    let sentCount = 0;

    this.clients.forEach((client, clientId) => {
      if (excludeClientId !== clientId && client.ws.readyState === 1) {
        client.ws.send(messageStr);
        sentCount++;
      }
    });

    return sentCount;
  }

  /**
   * Send message to specific clients by their IDs
   */
  sendToClients(clientIds, message) {
    const messageStr = JSON.stringify(message);
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
   */
  getClients() {
    const clientsList = [];
    this.clients.forEach((client, clientId) => {
      clientsList.push({
        id: clientId,
        connectedAt: client.connectedAt,
        ip: client.ip
      });
    });
    return clientsList;
  }

  /**
   * Get client count
   */
  getClientCount() {
    return this.clients.size;
  }

  /**
   * Disconnect a specific client
   */
  disconnectClient(clientId, reason = 'Disconnected by server') {
    const client = this.clients.get(clientId);
    if (client) {
      this.sendToClient(clientId, {
        type: 'disconnect',
        reason: reason
      });
      client.ws.close();
      return true;
    }
    return false;
  }

  /**
   * Stop the server
   */
  stop() {
    if (this.wss) {
      // Disconnect all clients
      this.clients.forEach((client, clientId) => {
        this.disconnectClient(clientId, 'Server shutting down');
      });

      this.wss.close(() => {
        console.log('Chat server stopped');
      });
    }
  }
}

export default ChatServer;
