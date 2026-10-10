#!/usr/bin/env node
/**
 * Connects to the backend's STOMP/SockJS notification endpoint as a real subscriber and
 * waits for a live-pushed notification. Exits 0 if one arrives before the timeout, 1
 * otherwise. Run from scripts/verify.sh, which starts this before firing the action that
 * should trigger the notification.
 *
 * Resolves @stomp/stompjs and ws from frontend/node_modules since this script has no
 * package.json of its own.
 */
const path = require('path');
const frontendModules = path.join(__dirname, '..', 'frontend', 'node_modules');
const { Client } = require(path.join(frontendModules, '@stomp', 'stompjs', 'bundles', 'stomp.umd.js'));
const WebSocket = require(path.join(frontendModules, 'ws'));

const backendUrl = process.argv[2];
const token = process.argv[3];
const timeoutMs = 25000;

if (!backendUrl || !token) {
  console.error('Usage: verify_ws.js <backendUrl> <accessToken>');
  process.exit(1);
}

const wsUrl = backendUrl.replace(/^http/, 'ws') + '/ws/websocket';

const client = new Client({
  webSocketFactory: () => new WebSocket(wsUrl),
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 0,
});

let settled = false;

const timer = setTimeout(() => {
  if (!settled) {
    settled = true;
    console.error(`Timed out after ${timeoutMs}ms waiting for a WebSocket notification`);
    client.deactivate();
    process.exit(1);
  }
}, timeoutMs);

client.onConnect = () => {
  console.error('STOMP connected, subscribing to /user/queue/notifications');
  client.subscribe('/user/queue/notifications', (message) => {
    if (settled) return;
    settled = true;
    clearTimeout(timer);
    console.error(`Received live notification: ${message.body}`);
    client.deactivate();
    process.exit(0);
  });
};

client.onStompError = (frame) => {
  console.error('STOMP error:', frame.headers['message']);
};

client.onWebSocketError = (event) => {
  console.error('WebSocket error:', event.message || event);
};

client.activate();
