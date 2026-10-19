import http from "k6/http";
import { check } from "k6";
import { Trend } from "k6/metrics";

/**
 * A 파트 조회 API 부하 — A_005 내 프로필, A_007 이웃 공개 프로필.
 * 세션 쿠키는 카카오 로그인으로만 발급 — 환경변수로 주입.
 *   k6 run -e SESSION=... -e TARGET=4 docs/performance/scripts/member-read.js
 */
const BASE = __ENV.BASE || "http://localhost:8080/api/v1";
const SESSION = __ENV.SESSION;
const TARGET = __ENV.TARGET || "4";

const me = new Trend("a005_me", true);
const neighbor = new Trend("a007_neighbor", true);

export const options = {
  stages: [
    { duration: "30s", target: 10 },
    { duration: "3m", target: 10 },
    { duration: "10s", target: 0 },
  ],
  thresholds: {
    "http_req_failed": ["rate<0.01"],
    "a005_me": ["p(95)<500"],
    "a007_neighbor": ["p(95)<500"],
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
