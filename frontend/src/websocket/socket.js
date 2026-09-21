import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'http://localhost:8080/ws';

let client = null;
// Subscriptions requested before the STOMP connection is actually up get
// queued here and flushed once by the single onConnect handler below.
// (Previously each subscribeWhenReady() call overwrote client.onConnect
// directly, silently dropping every earlier pending subscription whenever
// more than one was requested before the connection finished - e.g. the
// dispatcher dashboard subscribing to several ambulances at once.)
let pendingSubscriptions = [];

export function getStompClient() {
  if (client) return client;

  client = new Client({
    webSocketFactory: () => new SockJS(WS_BASE_URL),
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });

  client.onConnect = () => {
    const queued = pendingSubscriptions;
    pendingSubscriptions = [];
    queued.forEach(({ destination, callback, onSubscribed }) => {
      const sub = client.subscribe(destination, (msg) => callback(JSON.parse(msg.body)));
      onSubscribed(sub);
    });
  };

  client.activate();
  return client;
}

export function subscribeWhenReady(destination, callback) {
  const c = getStompClient();

  if (c.connected) {
    const sub = c.subscribe(destination, (msg) => callback(JSON.parse(msg.body)));
    return { unsubscribe: () => sub.unsubscribe() };
  }

  let sub = null;
  let unsubscribedBeforeConnect = false;
  pendingSubscriptions.push({
    destination,
    callback,
    onSubscribed: (s) => {
      if (unsubscribedBeforeConnect) {
        s.unsubscribe();
      } else {
        sub = s;
      }
    },
  });

  return {
    unsubscribe: () => {
      if (sub) sub.unsubscribe();
      else unsubscribedBeforeConnect = true;
    },
  };
}

export function publishLocation(ambulanceId, lat, lng) {
  const c = getStompClient();
  if (!c.connected) return;
  c.publish({
    destination: '/app/location.update',
    body: JSON.stringify({ ambulanceId, lat, lng }),
  });
}
