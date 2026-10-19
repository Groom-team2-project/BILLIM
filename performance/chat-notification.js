import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const data = JSON.parse(open('./local/fixtures.json'));
const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const testCase = __ENV.CASE || 'chat-send';
const vus = Number(__ENV.VUS || 2);
const duration = __ENV.DURATION || '15s';
const failures = new Rate('app_failures');
const deliveryFailures = new Rate('chat_delivery_failures');
const firstDeliveryFailures = new Rate('chat_first_delivery_failures');
const laterDeliveryFailures = new Rate('chat_later_delivery_failures');
const deliveryMs = new Trend('chat_delivery_ms', true);
const stableSocket = testCase === 'chat-websocket-stable';

export const options = {
  scenarios: testCase === 'notification-read'
    ? { selected: { executor: 'shared-iterations', vus, iterations: 80, maxDuration: '1m' } }
    : stableSocket
      ? { selected: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '30s' } }
      : { selected: { executor: 'constant-vus', vus, duration } },
  thresholds: {
    app_failures: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    ...(testCase === 'chat-websocket' || stableSocket ? {
      chat_delivery_ms: ['p(95)<2000'],
      chat_delivery_failures: ['rate<0.01'],
    } : {}),
    ...(stableSocket ? { chat_first_delivery_failures: ['rate<0.01'] } : {}),
  },
};

function pair() {
  return data.pairs[__ENV.HOT_ROOM === 'true' ? 0 : (__VU - 1) % data.pairs.length];
}

function headers(user, json = false) {
  return {
    Cookie: `BILLIM_SESSION=${user.token}`,
    'X-CSRF-TOKEN': user.csrf,
    ...(json ? { 'Content-Type': 'application/json' } : {}),
  };
}

function verify(response, expected) {
  const ok = check(response, { [`status ${expected}`]: (r) => r.status === expected });
  failures.add(!ok);
  return ok;
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const n = Math.floor(Math.random() * 16);
    return (c === 'x' ? n : (n & 3) | 8).toString(16);
  });
}

function send(room, user, clientMessageId) {
  return http.post(
    `${baseUrl}/api/v1/chat/rooms/${room}/messages`,
    JSON.stringify({ clientMessageId, body: 'Local performance test message' }),
    { headers: headers(user, true), tags: { endpoint: 'chat-send' } },
  );
}

export default function () {
  const current = pair();
  const user = current.requester;

  if (testCase === 'chat-send') {
    verify(send(current.roomId, user, uuid()), 201);
  } else if (testCase === 'chat-list') {
    verify(http.get(`${baseUrl}/api/v1/chat/rooms/${current.roomId}/messages?limit=20`,
      { headers: headers(user), tags: { endpoint: 'chat-list' } }), 200);
  } else if (testCase === 'chat-inbox') {
    verify(http.get(`${baseUrl}/api/v1/chat/rooms?filter=ALL&page=0&size=20`,
      { headers: headers(user), tags: { endpoint: 'chat-rooms' } }), 200);
    verify(http.get(`${baseUrl}/api/v1/chat/unread-count`,
      { headers: headers(user), tags: { endpoint: 'chat-unread-count' } }), 200);
  } else if (testCase === 'notification-list') {
    verify(http.get(`${baseUrl}/api/v1/notifications?limit=20`,
      { headers: headers(user), tags: { endpoint: 'notification-list' } }), 200);
    verify(http.get(`${baseUrl}/api/v1/notifications/unread-count`,
      { headers: headers(user), tags: { endpoint: 'notification-unread-count' } }), 200);
  } else if (testCase === 'notification-read') {
    const id = current.notificationIds[__ITER % current.notificationIds.length];
    verify(http.put(`${baseUrl}/api/v1/notifications/${id}/read`, null,
      { headers: headers(user), tags: { endpoint: 'notification-read' } }), 200);
  } else if (testCase === 'chat-websocket') {
    const clientMessageId = uuid();
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    let received = false;
    let sentAt = 0;
    const result = ws.connect(wsUrl, { headers: headers(user), tags: { endpoint: 'chat-websocket' } }, (socket) => {
      socket.on('open', () => {
        const sendAfterOpen = () => {
          sentAt = Date.now();
          verify(send(current.roomId, user, clientMessageId), 201);
        };
        const delay = Number(__ENV.WS_SEND_DELAY_MS || 0);
        if (delay > 0) socket.setTimeout(sendAfterOpen, delay);
        else sendAfterOpen();
      });
      socket.on('message', (raw) => {
        try {
          if (JSON.parse(raw).clientMessageId === clientMessageId) {
            received = true;
            deliveryMs.add(Date.now() - sentAt);
            socket.close();
          }
        } catch (_) { /* Invalid payload is counted as a delivery failure below. */ }
      });
      socket.setTimeout(() => socket.close(), 5000);
    });
    const deliveryFailed = !received || result.status !== 101;
    deliveryFailures.add(deliveryFailed);
    failures.add(deliveryFailed);
  } else if (stableSocket) {
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    const sent = new Map();
    const received = new Set();
    let count = 0;
    const sendNext = () => {
      if (count >= 10) return;
      const id = uuid();
      sent.set(id, { index: count, at: Date.now() });
      count += 1;
      verify(send(current.roomId, user, id), 201);
    };
    const result = ws.connect(wsUrl, { headers: headers(user), tags: { endpoint: 'chat-websocket-stable' } }, (socket) => {
      socket.on('open', () => {
        sendNext();
        socket.setInterval(sendNext, 200);
      });
      socket.on('message', (raw) => {
        try {
          const id = JSON.parse(raw).clientMessageId;
          if (sent.has(id) && !received.has(id)) {
            received.add(id);
            deliveryMs.add(Date.now() - sent.get(id).at);
            if (received.size === 10) socket.close();
          }
        } catch (_) { /* Count missing deliveries after the socket closes. */ }
      });
      socket.setTimeout(() => socket.close(), 5000);
    });
    for (const [id, detail] of sent) {
      const missed = !received.has(id) || result.status !== 101;
      deliveryFailures.add(missed);
      if (detail.index === 0) firstDeliveryFailures.add(missed);
      else laterDeliveryFailures.add(missed);
      failures.add(missed);
    }
  } else {
    throw new Error(`Unknown CASE: ${testCase}`);
  }

  if (testCase !== 'notification-read') sleep(0.2);
}
