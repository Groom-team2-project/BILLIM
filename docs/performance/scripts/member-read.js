import http from "k6/http";
import { check } from "k6";
import { Trend } from "k6/metrics";

/**
 * A 파트 조회 API 부하 — A_005 내 프로필, A_007 이웃 공개 프로필.
 * 세션 쿠키는 카카오 로그인으로만 발급 — 환경변수로 주입.
 *   k6 run -e SESSION=... -e TARGET=4 docs/performance/scripts/member-read.js
 * 처리량·응답시간 측정 시 SQL 로그 off — 로그 비용이 결과를 가림.
 */
const BASE = __ENV.BASE || "http://localhost:8080/api/v1";
const SESSION = __ENV.SESSION;
const TARGET = __ENV.TARGET || "4";
const STEADY = __ENV.STEADY || "3m";          // 반복 측정 시 단축 가능
const VUS = Number(__ENV.VUS || 10);          // 포화 곡선용 — 단계별로 올린다
const RAMP = __ENV.RAMP || "30s";

const me = new Trend("a005_me", true);
const neighbor = new Trend("a007_neighbor", true);

export const options = {
  stages: [
    { duration: RAMP, target: VUS },
    { duration: STEADY, target: VUS },
    { duration: "10s", target: 0 },
  ],
  // 기본 요약에 p99 미포함 — 명시해야 출력
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
  thresholds: {
    // 포화 구간에서는 넘기는 게 정상 — 중단시키지 않고 기록만
    "http_req_failed": [{ threshold: "rate<0.01", abortOnFail: false }],
    "a005_me": [{ threshold: "p(95)<500", abortOnFail: false }],
    "a007_neighbor": [{ threshold: "p(95)<500", abortOnFail: false }],
  },
};

const params = {
  headers: { "X-Requested-With": "XMLHttpRequest", Cookie: `BILLIM_SESSION=${SESSION}` },
};

export default function () {
  const a = http.get(`${BASE}/members/me`, { ...params, tags: { name: "A_005 /members/me" } });
  check(a, { "A_005 200": (r) => r.status === 200 });
  me.add(a.timings.duration);

  const b = http.get(`${BASE}/members/${TARGET}`, { ...params, tags: { name: "A_007 /members/{id}" } });
  check(b, { "A_007 200": (r) => r.status === 200 });
  neighbor.add(b.timings.duration);
}
