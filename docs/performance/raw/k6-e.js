// E 파트(커뮤니티·운영) 읽기 부하. 엔드포인트별 p95·p99를 따로 본다.
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://host.docker.internal:8080/api/v1';
const USER = __ENV.USER_TOKEN;
const ADMIN = __ENV.ADMIN_TOKEN;

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '60s',
  // 요청별 지표를 따로 보므로 전역 임계값은 실패율만 둔다
  thresholds: { http_req_failed: ['rate<0.01'] },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};

// k6 지표 이름은 ASCII만 허용한다. 한글 표기는 결과를 쓸 때 붙인다
const LABEL = {
  nearby: 'E_071 주변동네', search: 'E_009 검색', list: 'E_009 목록',
  detail: 'E_010 상세', places: 'E_012 장소', my_reports: '내 신고내역',
  notices: '공지 목록', blocks: '차단 목록',
  admin_reports_p0: '관리자 신고 p0', admin_reports_p500: '관리자 신고 p500',
};
const NAMES = Object.keys(LABEL);
const T = {};
for (const n of NAMES) T[n] = new Trend(n, true);

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function setup() {
  const r = http.get(`${BASE}/auth/csrf`, { headers: { Cookie: `BILLIM_SESSION=${USER}` } });
  return { csrf: r.json('csrfToken') };
}

export default function (data) {
  const u = { headers: { Cookie: `BILLIM_SESSION=${USER}` } };
  const a = { headers: { Cookie: `BILLIM_SESSION=${ADMIN}` } };
  const cid = 1 + ((__ITER * 7) % 3500);          // 상세는 동네를 돌려가며 조회해 캐시 편향을 줄인다
  const MY = 3;                                   // 장소·공지는 소속 동네만 열람 가능
  let res;

  res = http.post(`${BASE}/communities/nearby`,
    JSON.stringify({ latitude: 37.5 + (Math.random() - 0.5) * 0.2, longitude: 127.0 + (Math.random() - 0.5) * 0.2, accuracyMeters: 30 }),
    { headers: { Cookie: `BILLIM_SESSION=${USER}`, 'Content-Type': 'application/json', 'X-CSRF-TOKEN': data.csrf, 'Idempotency-Key': uuid() } });
  T.nearby.add(res.timings.duration); check(res, { 'nearby 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/communities?keyword=%EC%97%AD%EC%82%BC&page=0&size=20`, u);
  T.search.add(res.timings.duration); check(res, { 'search 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/communities?page=0&size=20`, u);
  T.list.add(res.timings.duration); check(res, { 'list 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/communities/${cid}`, u);
  T.detail.add(res.timings.duration); check(res, { 'detail 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/communities/${MY}/places`, u);
  T.places.add(res.timings.duration); check(res, { 'places 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/members/me/reports?page=0&size=20`, u);
  T.my_reports.add(res.timings.duration); check(res, { 'myreports 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/communities/${MY}/notices?page=0&size=20`, u);
  T.notices.add(res.timings.duration); check(res, { 'notices 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/members/me/blocks?page=0&size=20`, u);
  T.blocks.add(res.timings.duration); check(res, { 'blocks 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/admin/reports?page=0&size=20`, a);
  T.admin_reports_p0.add(res.timings.duration); check(res, { 'admin p0 200': (r) => r.status === 200 });

  res = http.get(`${BASE}/admin/reports?page=500&size=20`, a);
  T.admin_reports_p500.add(res.timings.duration); check(res, { 'admin p500 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  const out = {};
  for (const n of NAMES) {
    const m = data.metrics[n];
    if (m) out[LABEL[n]] = { avg: m.values.avg, p95: m.values['p(95)'], p99: m.values['p(99)'], max: m.values.max};
  }
  out._total = {
    reqs: data.metrics.http_reqs.values.count,
    rps: data.metrics.http_reqs.values.rate,
    failed: data.metrics.http_req_failed.values.rate,
    avg: data.metrics.http_req_duration.values.avg,
    p95: data.metrics.http_req_duration.values['p(95)'],
    p99: data.metrics.http_req_duration.values['p(99)'],
  };
  out._checks = {};
  for (const g of (data.root_group.checks || [])) out._checks[g.name] = { pass: g.passes, fail: g.fails };
  return { [`/work/${__ENV.OUT || 'k6-result'}.json`]: JSON.stringify(out, null, 2) };
}
