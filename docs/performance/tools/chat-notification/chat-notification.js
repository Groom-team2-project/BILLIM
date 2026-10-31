import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const data = JSON.parse(open(`./local/${__ENV.FIXTURE_NAME || 'fixtures.json'}`));
const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const testCase = __ENV.CASE || 'chat-send';
const vus = Number(__ENV.VUS || 2);
const duration = __ENV.DURATION || '15s';
const failures = new Rate('app_failures');
const deliveryFailures = new Rate('chat_delivery_failures');
const connectionFailures = new Rate('chat_websocket_connection_failures');
const incompleteIterations = new Rate('chat_websocket_incomplete_iterations');
const firstDeliveryFailures = new Rate('chat_first_delivery_failures');
const laterDeliveryFailures = new Rate('chat_later_delivery_failures');
const peerReceiptFailures = new Rate('chat_peer_receipt_failures');
const deliveryMs = new Trend('chat_delivery_ms', true);
const stableSocket = testCase === 'chat-websocket-stable';
const realisticSocket = testCase === 'chat-realistic';
const pairedSocket = testCase === 'chat-paired';
const idleSocket = testCase === 'chat-idle';

export const options = {
  scenarios: testCase === 'notification-read'
    ? { selected: { executor: 'shared-iterations', vus, iterations: 80, maxDuration: '1m' } }
    : idleSocket
      ? { selected: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '60s' } }
    : stableSocket || realisticSocket || pairedSocket
      ? { selected: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '30s' } }
      : { selected: { executor: 'constant-vus', vus, duration } },
  thresholds: {
    app_failures: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    ...(testCase === 'chat-websocket' || stableSocket || realisticSocket || pairedSocket ? {
      chat_delivery_ms: ['p(95)<2000'],
      chat_websocket_connection_failures: ['rate<0.01'],
    } : {}),
    ...(!pairedSocket && (testCase === 'chat-websocket' || stableSocket || realisticSocket) ? {
      chat_delivery_failures: ['rate<0.01'],
    } : {}),
    ...(stableSocket || realisticSocket ? {
      chat_first_delivery_failures: ['rate<0.01'],
      chat_websocket_incomplete_iterations: ['rate<0.01'],
    } : {}),
    ...(pairedSocket ? {
      chat_peer_receipt_failures: ['rate<0.01'],
      chat_websocket_incomplete_iterations: ['rate<0.01'],
    } : {}),
  },
};

function pair() {
  if (pairedSocket || idleSocket) return data.pairs[Math.floor((__VU - 1) / 2)];
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

function send(room, user, clientMessageId, body = 'Local performance test message') {
  return http.post(
    `${baseUrl}/api/v1/chat/rooms/${room}/messages`,
    JSON.stringify({ clientMessageId, body }),
    { headers: headers(user, true), tags: { endpoint: 'chat-send' } },
  );
}

export function setup() {
  return { runId: uuid() };
}

export default function (context) {
  if (pairedSocket && (vus % 2 !== 0 || vus > data.pairs.length * 2)) {
    throw new Error('chat-paired needs an even VUS and one unique room per two VUs');
  }
  const current = pair();
  const user = idleSocket && __VU % 2 === 1 ? current.owner : current.requester;

  if (testCase === 'chat-send') {
    verify(send(current.roomId, user, uuid()), 201);
  } else if (testCase === 'chat-list') {
    verify(http.get(`${baseUrl}/api/v1/chat/rooms/${current.roomId}/messages?limit=20`,
      { headers: headers(user), tags: { endpoint: 'chat-list' } }), 200);
  } else if (testCase === 'chat-room-list') {
    const size = Number(__ENV.INBOX_PAGE_SIZE || 20);
    const response = http.get(`${baseUrl}/api/v1/chat/rooms?filter=ALL&page=0&size=${size}`,
      { headers: headers(current.owner), tags: { endpoint: 'chat-room-list' } });
    const ok = check(response, {
      'room list has requested size': (r) => r.status === 200 && r.json('items').length === size,
    });
    failures.add(!ok);
    sleep(0.2);
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
  } else if (idleSocket) {
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    const result = ws.connect(wsUrl, { headers: headers(user) }, (socket) => {
      socket.on('open', () => console.log(`PERF_IDLE_READY ${__VU}`));
      socket.setTimeout(() => socket.close(), 45000);
    });
    verify(result, 101);
  } else if (testCase === 'chat-websocket') {
    const clientMessageId = uuid();
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    let received = false;
    let sentAt = 0;
    let sendSucceeded = false;
    const result = ws.connect(wsUrl, { headers: headers(user), tags: { endpoint: 'chat-websocket' } }, (socket) => {
      socket.on('open', () => {
        const sendAfterOpen = () => {
          sentAt = Date.now();
          sendSucceeded = verify(send(current.roomId, user, clientMessageId), 201);
        };
        const delay = Number(__ENV.WS_SEND_DELAY_MS || 0);
        if (delay > 0) socket.setTimeout(sendAfterOpen, delay);
        else sendAfterOpen();
      });
      socket.on('message', (raw) => {
        try {
          if (JSON.parse(raw).clientMessageId === clientMessageId) {
            received = true;
            if (sendSucceeded) deliveryMs.add(Date.now() - sentAt);
            socket.close();
          }
        } catch (_) { /* Invalid payload is counted as a delivery failure below. */ }
      });
      socket.setTimeout(() => socket.close(), 5000);
    });
    const connectionFailed = result.status !== 101;
    connectionFailures.add(connectionFailed);
    failures.add(connectionFailed);
    if (sendSucceeded) {
      const deliveryFailed = !received;
      deliveryFailures.add(deliveryFailed);
      failures.add(deliveryFailed);
    }
  } else if (stableSocket) {
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    const sent = new Map();
    const received = new Set();
    let count = 0;
    const sendNext = () => {
      if (count >= 10) return;
      const id = uuid();
      const detail = { index: count, at: Date.now(), succeeded: false };
      sent.set(id, detail);
      count += 1;
      detail.succeeded = verify(send(current.roomId, user, id), 201);
    };
    const result = ws.connect(wsUrl, { headers: headers(user), tags: { endpoint: 'chat-websocket-stable' } }, (socket) => {
      socket.on('open', () => {
        sendNext();
        socket.setInterval(sendNext, 200);
      });
      socket.on('message', (raw) => {
        try {
          const id = JSON.parse(raw).clientMessageId;
          if (sent.get(id)?.succeeded && !received.has(id)) {
            received.add(id);
            deliveryMs.add(Date.now() - sent.get(id).at);
            if (received.size === 10) socket.close();
          }
        } catch (_) { /* Count missing deliveries after the socket closes. */ }
      });
      socket.setTimeout(() => socket.close(), 5000);
    });
    const connectionFailed = result.status !== 101;
    connectionFailures.add(connectionFailed);
    const successfulSends = [...sent.values()].filter((detail) => detail.succeeded).length;
    const incomplete = successfulSends !== 10;
    incompleteIterations.add(incomplete);
    failures.add(connectionFailed || incomplete);
    for (const [id, detail] of sent) {
      if (!detail.succeeded) continue;
      const missed = !received.has(id);
      deliveryFailures.add(missed);
      if (detail.index === 0) firstDeliveryFailures.add(missed);
      else laterDeliveryFailures.add(missed);
      failures.add(missed);
    }
  } else if (realisticSocket) {
    if (__VU > data.pairs.length) throw new Error('chat-realistic needs at least one unique pair per VU');
    const receiver = current.requester;
    const sender = current.owner;
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    const sent = new Map();
    const received = new Set();
    let count = 0;
    const sendNext = () => {
      if (count >= 5) return;
      const id = uuid();
      const detail = { index: count, at: Date.now(), succeeded: false };
      sent.set(id, detail);
      count += 1;
      detail.succeeded = verify(send(current.roomId, sender, id), 201);
    };
    const result = ws.connect(wsUrl, { headers: headers(receiver), tags: { endpoint: 'chat-realistic' } }, (socket) => {
      socket.on('open', () => {
        socket.setTimeout(() => {
          sendNext();
          socket.setInterval(sendNext, 2000);
        }, 1000);
      });
      socket.on('message', (raw) => {
        try {
          const id = JSON.parse(raw).clientMessageId;
          if (sent.get(id)?.succeeded && !received.has(id)) {
            received.add(id);
            deliveryMs.add(Date.now() - sent.get(id).at);
            if (received.size === 5) socket.close();
          }
        } catch (_) { /* 수신 종료 후 성공한 전송 중 누락 건수를 집계. */ }
      });
      socket.setTimeout(() => socket.close(), 15000);
    });
    const connectionFailed = result.status !== 101;
    connectionFailures.add(connectionFailed);
    const successfulSends = [...sent.values()].filter((detail) => detail.succeeded).length;
    const incomplete = successfulSends !== 5;
    incompleteIterations.add(incomplete);
    failures.add(connectionFailed || incomplete);
    for (const [id, detail] of sent) {
      if (!detail.succeeded) continue;
      const missed = !received.has(id);
      deliveryFailures.add(missed);
      if (detail.index === 0) firstDeliveryFailures.add(missed);
      else laterDeliveryFailures.add(missed);
      failures.add(missed);
    }
  } else if (pairedSocket) {
    const ownerSide = (__VU - 1) % 2 === 0;
    const sender = ownerSide ? current.owner : current.requester;
    const peer = ownerSide ? current.requester : current.owner;
    const marker = `${context.runId}|`;
    const wsUrl = baseUrl.replace(/^http/, 'ws') + `/ws/chat?roomId=${current.roomId}`;
    const sent = new Map();
    const ownReceived = new Set();
    const peerReceived = new Set();
    let count = 0;
    const sendNext = () => {
      if (count >= 5) return;
      const id = uuid();
      const detail = { succeeded: false };
      sent.set(id, detail);
      count += 1;
      detail.succeeded = verify(send(current.roomId, sender, id, `${marker}${Date.now()}`), 201);
    };
    const result = ws.connect(wsUrl, { headers: headers(sender), tags: { endpoint: 'chat-paired' } }, (socket) => {
      socket.on('open', () => {
        socket.setTimeout(() => {
          sendNext();
          socket.setInterval(sendNext, 2000);
        }, 3000);
      });
      socket.on('message', (raw) => {
        try {
          const message = JSON.parse(raw);
          if (!message.body?.startsWith(marker)) return;
          if (message.senderId === String(sender.memberId) && sent.get(message.clientMessageId)?.succeeded) {
            ownReceived.add(message.clientMessageId);
          } else if (message.senderId === String(peer.memberId) && !peerReceived.has(message.clientMessageId)) {
            peerReceived.add(message.clientMessageId);
            deliveryMs.add(Date.now() - Number(message.body.slice(marker.length)));
          }
          if (count === 5 && ownReceived.size === 5 && peerReceived.size === 5) socket.close();
        } catch (_) { /* 현재 실행의 메시지 외 수신은 종료 시 누락 건수로 확인. */ }
      });
      socket.setTimeout(() => socket.close(), 17000);
    });
    const connectionFailed = result.status !== 101;
    connectionFailures.add(connectionFailed);
    const successfulSends = [...sent.values()].filter((detail) => detail.succeeded).length;
    const incomplete = successfulSends !== 5 || ownReceived.size !== successfulSends;
    incompleteIterations.add(incomplete);
    failures.add(connectionFailed || incomplete);
    for (let index = 0; index < 5; index += 1) {
      const missed = index >= peerReceived.size;
      peerReceiptFailures.add(missed);
      failures.add(missed);
    }
  } else {
    throw new Error(`Unknown CASE: ${testCase}`);
  }

  if (testCase !== 'notification-read' && !realisticSocket && !pairedSocket && !idleSocket) sleep(0.2);
}
