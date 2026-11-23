import http from "k6/http";
import { check } from "k6";

/**
 * 인스턴스 여러 대에 A_005·A_007 조회 부하 분산. VU 번호로 인스턴스 고정 배정(라운드 로빈).
 *   k6 run -e BASES=http://localhost:18080,http://localhost:18081 -e VUS=80 member-read-multi.js
 * 세션은 session.sql이 넣은 쿠키 원문 사용.
 */
const BASES = (__ENV.BASES || "http://localhost:18080").split(",").map((b) => `${b}/api/v1`);
const SESSION = __ENV.SESSION || "perf-session-0001";
const TARGET = __ENV.TARGET || "4";
const VUS = Number(__ENV.VUS || 80);

export const options = {
  stages: [
    { duration: __ENV.RAMP || "15s", target: VUS },
    { duration: __ENV.STEADY || "60s", target: VUS },
    { duration: "5s", target: 0 },
  ],
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
  // 실패 1% 이상이면 종료 코드 비정상 — pool-bench.sh가 그 회차를 측정값으로 쓰지 않고 중단
  thresholds: { http_req_failed: ["rate<0.01"], checks: ["rate>0.99"] },
};

// 서명 강제 운영 서버 직접 측정 시 nf_sign.py 출력 전달. 로컬은 생략
const headers = { Cookie: `BILLIM_SESSION=${SESSION}` };
if (__ENV.NF_SIGN) headers["x-nf-sign"] = __ENV.NF_SIGN;
const params = { headers };

export default function () {
  const base = BASES[(__VU - 1) % BASES.length];
  const a = http.get(`${base}/members/me`, params);
  const b = http.get(`${base}/members/${TARGET}`, params);
  check(a, { "A_005 200": (r) => r.status === 200 });
  check(b, { "A_007 200": (r) => r.status === 200 });
}
