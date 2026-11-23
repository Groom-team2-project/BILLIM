import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const SESSION = __ENV.SESSION;
const ROLE = __ENV.ROLE || "BORROWER";
if (!SESSION) throw new Error("SESSION에 테스트 계정의 BILLIM_SESSION 쿠키 값 지정 필요");
if (!["BORROWER", "OWNER"].includes(ROLE)) throw new Error("ROLE은 BORROWER 또는 OWNER만 허용");

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || "3m",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate==1"],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/api/v1/rentals?role=${ROLE}&page=0&size=20`, {
    cookies: { BILLIM_SESSION: SESSION },
    redirects: 0,
    timeout: "10s",
    tags: { name: "GET /api/v1/rentals" },
  });
  check(response, {
    "목록 조회 200": (r) => r.status === 200,
    "대여 목록 응답 형식": (r) => {
      try {
        return Array.isArray(r.json().items);
      } catch {
        return false;
      }
    },
  });
  sleep(1);
}
