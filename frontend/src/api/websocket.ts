import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Notification } from '@/types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
// The STOMP endpoint is mounted on the backend root ("/ws"), not under "/api".
const WS_ENDPOINT = `${API_BASE_URL.replace(/\/api\/?$/, '')}/ws`;

let client: Client | null = null;

export function connectNotifications(onNotification: (notification: Notification) => void): void {
  if (client?.active) {
    return;
  }

  const token = localStorage.getItem('accessToken');

  client = new Client({
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 5000,
    onConnect: () => {
      client?.subscribe('/user/queue/notifications', (message: IMessage) => {
        try {
          const notification: Notification = JSON.parse(message.body);
          onNotification(notification);
        } catch (error) {
          console.error('Failed to parse notification payload', error);
        }
      });
    },
    onStompError: (frame) => {
      console.error('WebSocket STOMP error', frame.headers['message']);
    },
  });

  client.activate();
}

export function disconnectNotifications(): void {
  client?.deactivate();
  client = null;
}
