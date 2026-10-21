// B파트(물건·검색) 조회 성능 측정 — k6
// 전제: python3 performance/item_seed.py 실행 완료 (local/item-fixtures.json 생성)
// 실행: docker compose -f performance/compose.yaml run --rm -e CASE=search-latest -e VUS=10 -e DURATION=30s k6 run /scripts/item-read.js
// 반복 측정과 표 정리는 performance/item_bench.sh 사용

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

let fixture;
try {
  fixture = JSON.parse(open('./local/item-fixtures.json'));
} catch (e) {
  throw new Error('local/item-fixtures.json 이 없습니다. 먼저 python3 performance/item_seed.py 를 실행하세요.');
}

const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const testCase = __ENV.CASE || 'search-latest';
const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';
const think = Number(__ENV.THINK || 0.2);

const appFailures = new Rate('app_failures');
const clientErrors = new Counter('client_errors_4xx');
const serverErrors = new Counter('server_errors_5xx');

// 날짜는 서버 검증 기준(KST)으로 계산
function kstDate(offsetDays) {
  const d = new Date(Date.now() + 9 * 3600 * 1000 + offsetDays * 86400 * 1000);
  return d.toISOString().slice(0, 10);
}

const CASES = {
  'search-latest': () => '/api/v1/items?page=0&size=20&sort=LATEST',
  'search-keyword': () => `/api/v1/items?keyword=${encodeURIComponent('드릴')}&page=0&size=20&sort=LATEST`,
  'search-category': () => `/api/v1/items?categoryId=${fixture.categoryId}&page=0&size=20&sort=LATEST`,
  'search-period': () => `/api/v1/items?startDate=${kstDate(1)}&endDate=${kstDate(7)}&page=0&size=20&sort=LATEST`,
  'search-nearest': () => '/api/v1/items?page=0&size=20&sort=NEAREST',
  'search-deep-page': () => `/api/v1/items?page=${Number(__ENV.DEEP_PAGE || 1000)}&size=20&sort=LATEST`,
  'item-detail': () => `/api/v1/items/${fixture.detailItemId}`,
  mine: () => '/api/v1/members/me/items?page=0&size=20',
  neighbor: () => `/api/v1/members/${fixture.targetMemberId}/items?page=0&size=20`,
  map: () => '/api/v1/items/map?minLat=34.99&maxLat=35.01&minLng=126.99&maxLng=127.01',
  home: () => '/api/v1/home',
};

if (!CASES[testCase]) {
  throw new Error(`알 수 없는 CASE: ${testCase}. 가능한 값: ${Object.keys(CASES).join(', ')}`);
}

export const options = {
  scenarios: { selected: { executor: 'constant-vus', vus, duration } },
  thresholds: {
    app_failures: ['rate<0.01'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const params = {
  headers: { Cookie: `BILLIM_SESSION=${fixture.viewer.token}`, Accept: 'application/json' },
  tags: { endpoint: testCase },
};

export default function () {
  const res = http.get(`${baseUrl}${CASES[testCase]()}`, params);
  if (res.status >= 500) serverErrors.add(1);
  else if (res.status >= 400) clientErrors.add(1);
  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'JSON 응답': (r) => (r.headers['Content-Type'] || '').includes('application/json'),
  });
  appFailures.add(!ok);
  sleep(think);
}

function num(v, digits = 1) {
  return v === undefined || v === null ? null : Number(Number(v).toFixed(digits));
}

// 사람이 읽는 요약 + item_report.py 가 읽는 한 줄(BILLIM_RESULT)
export function handleSummary(data) {
  const d = data.metrics.http_req_duration.values;
  const result = {
    case: testCase,
    vus,
    duration,
    reqPerSec: num(data.metrics.http_reqs.values.rate, 2),
    requests: data.metrics.http_reqs.values.count,
    avg: num(d.avg), p50: num(d['p(50)']), p95: num(d['p(95)']), p99: num(d['p(99)']), max: num(d.max),
    failRate: num(data.metrics.http_req_failed.values.rate * 100, 2),
    client4xx: data.metrics.client_errors_4xx ? data.metrics.client_errors_4xx.values.count : 0,
    server5xx: data.metrics.server_errors_5xx ? data.metrics.server_errors_5xx.values.count : 0,
    p95Pass: d['p(95)'] < 500,
  };
  const text = [
    `CASE ${result.case} · VU ${vus} · ${duration}`,
    `  처리량 ${result.reqPerSec} req/s (총 ${result.requests}건)`,
    `  응답 p50 ${result.p50}ms · p95 ${result.p95}ms · p99 ${result.p99}ms · 최대 ${result.max}ms`,
    `  실패율 ${result.failRate}% · 4xx ${result.client4xx}건 · 5xx ${result.server5xx}건`,
    `  판정 ${result.p95Pass && result.failRate < 1 ? '통과' : '기준 미달'} (p95 < 500ms · 오류율 < 1%)`,
    `BILLIM_RESULT ${JSON.stringify(result)}`,
    '',
  ].join('\n');
  return { stdout: text };
}
