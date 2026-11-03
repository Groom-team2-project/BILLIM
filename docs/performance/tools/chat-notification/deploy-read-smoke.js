import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Rate, Trend } from 'k6/metrics';

// 운영 조회 점검 전용. CLI로 VU·기간을 덮어쓰는 실행 대신 제공된 PowerShell 실행기 사용.
const config = JSON.parse(open(__ENV.SESSION_FILE || '/config/session.json'));
const base = (__ENV.BASE_URL || '').replace(/\/$/, '');
if (!/^https?:\/\/[^/?#@\s]+$/.test(base)) throw new Error('BASE_URL must be an HTTP(S) origin');
if (!config.sessionToken || /[\s;\r\n]/.test(config.sessionToken)) throw new Error('Valid session token required');
if (config.roomId && !/^[1-9]\d*$/.test(String(config.roomId))) throw new Error('Invalid room ID');
const requestCount = Number(__ENV.REQUEST_COUNT || 24);
if (!Number.isInteger(requestCount) || requestCount < 4 || requestCount > 60) throw new Error('Request count must be 4..60');
const targets = [
  ['chat_rooms', '/api/v1/chat/rooms?filter=ALL&page=0&size=20', 'items'],
  ['chat_unread', '/api/v1/chat/unread-count', 'unreadCount'],
  ['notifications', '/api/v1/notifications?limit=20', 'items'],
  ['notification_unread', '/api/v1/notifications/unread-count', 'unreadCount'],
];
if (config.roomId) targets.push(['messages', `/api/v1/chat/rooms/${config.roomId}/messages?limit=20`, 'items']);
const timings = Object.fromEntries(targets.map(([name]) => [name, new Trend(`endpoint_${name}_ms`, true)]));
const failures = new Rate('app_failures');
const headers = { Cookie: `BILLIM_SESSION=${config.sessionToken}` };
if (config.nfSign) headers['x-nf-sign'] = config.nfSign;

export const options = {
  scenarios: { gentle_reads: { executor: 'shared-iterations', vus: 1, iterations: requestCount,
    maxDuration: '2m', gracefulStop: '0s' } },
  maxRedirects: 0,
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
  thresholds: { app_failures: ['rate==0'], http_req_failed: ['rate==0'] },
};

export default function () {
  const [name, path, field] = targets[exec.scenario.iterationInTest % targets.length];
  const response = http.get(base + path, { headers, redirects: 0, timeout: '5s', tags: { name } });
  timings[name].add(response.timings.duration);
  let valid = false;
  try {
    const body = response.json();
    valid = response.status === 200 && (field === 'items'
      ? Array.isArray(body.items) && body.items.length <= 20
      : Number.isInteger(body.unreadCount) && body.unreadCount >= 0);
  } catch (_) { /* JSON 변환 실패도 응답 검증 실패로 처리. 응답 본문 출력 금지. */ }
  check(valid, { 'valid read response': value => value });
  failures.add(!valid);
  if (!valid) exec.test.abort(`${name}: HTTP ${response.status}, response validation failed`);
  sleep(1);
}
