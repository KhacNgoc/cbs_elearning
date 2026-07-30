import type { AppNotification } from '../api/notificationApi';

export class NotificationSocket {
  private socket: WebSocket | null = null;
  private stopped = false;
  private attempt = 0;
  private reconnectTimer?: number;
  private heartbeatTimer?: number;
  private pongTimer?: number;
  private connectTimer?: number;
  private readonly onNotification: (value: AppNotification) => void;
  private readonly onStatus: (connected: boolean) => void;

  constructor(
    onNotification: (value: AppNotification) => void,
    onStatus: (connected: boolean) => void
  ) {
    this.onNotification = onNotification;
    this.onStatus = onStatus;
  }

  start() {
    this.stopped = false;
    this.connect();
    window.addEventListener('online', this.handleOnline);
    document.addEventListener('visibilitychange', this.handleVisibility);
  }

  stop() {
    this.stopped = true;
    this.clearTimers();
    window.removeEventListener('online', this.handleOnline);
    document.removeEventListener('visibilitychange', this.handleVisibility);
    this.socket?.close(1000, 'Client stopped');
    this.socket = null;
    this.onStatus(false);
  }

  private connect() {
    if (this.stopped || !navigator.onLine || this.socket) return;
    const token = localStorage.getItem('accessToken');
    if (!token) return;
    const url = new URL(import.meta.env.VITE_API_URL || window.location.origin);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = '/ws/notifications';
    url.search = '';

    const socket = new WebSocket(url.toString());
    this.socket = socket;
    this.connectTimer = window.setTimeout(() => {
      if (socket.readyState !== WebSocket.OPEN) socket.close();
    }, 10_000);
    socket.onopen = () => {
      this.clearConnectTimer();
      socket.send(JSON.stringify({ type: 'AUTH', token }));
    };
    socket.onmessage = event => this.handleMessage(event);
    socket.onerror = () => socket.close();
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.onStatus(false);
      this.clearHeartbeat();
      this.scheduleReconnect();
    };
  }

  private handleMessage(event: MessageEvent<string>) {
    try {
      const message = JSON.parse(event.data);
      if (message.type === 'AUTHENTICATED') {
        this.attempt = 0;
        this.onStatus(true);
        this.startHeartbeat();
      } else if (message.type === 'PONG') {
        if (this.pongTimer) window.clearTimeout(this.pongTimer);
        this.pongTimer = undefined;
      } else if (message.type === 'NOTIFICATION' && message.data) {
        this.onNotification(message.data as AppNotification);
      }
    } catch {
      // Ignore malformed frames.
    }
  }

  private startHeartbeat() {
    this.clearHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      if (this.socket?.readyState !== WebSocket.OPEN) return;
      this.socket.send(JSON.stringify({ type: 'PING' }));
      if (this.pongTimer) window.clearTimeout(this.pongTimer);
      this.pongTimer = window.setTimeout(() => this.socket?.close(), 10_000);
    }, 20_000);
  }

  private scheduleReconnect() {
    if (this.stopped || this.reconnectTimer) return;
    const delay = Math.min(30_000, 1_000 * 2 ** this.attempt++) + Math.round(Math.random() * 500);
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = undefined;
      this.connect();
    }, delay);
  }

  private handleOnline = () => {
    if (!this.socket) this.connect();
  };
  private handleVisibility = () => {
    if (document.visibilityState === 'visible' && !this.socket) this.connect();
  };
  private clearConnectTimer() {
    if (this.connectTimer) window.clearTimeout(this.connectTimer);
    this.connectTimer = undefined;
  }
  private clearHeartbeat() {
    if (this.heartbeatTimer) window.clearInterval(this.heartbeatTimer);
    if (this.pongTimer) window.clearTimeout(this.pongTimer);
    this.heartbeatTimer = undefined;
    this.pongTimer = undefined;
  }
  private clearTimers() {
    this.clearConnectTimer();
    this.clearHeartbeat();
    if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = undefined;
  }
}
