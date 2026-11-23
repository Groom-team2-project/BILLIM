import http from "k6/http";
import { check, sleep } from "k6";

// 토큰은 추적하지 않는 로컬 임시 파일에서만 읽기. 출력·결과 파일에 저장 금지.
const fixture = JSON.parse(open(__ENV.RENTAL_LIST_FIXTURE));
const testCase = __ENV.RENTAL_LIST_CASE;
const entry = fixture.cases[testCase];
if (!entry) throw new Error("측정 목록 종류 확인 필요");
// JSON 객체의 키 순서는 계약에 포함되지 않으므로 정렬 후 전체 값 비교.
function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}
const expected = canonical(entry.expected);
export const options = {
  vus: 10, duration: __ENV.DURATION || "30s",
  thresholds: { checks: ["rate==1"], http_req_failed: ["rate==0"] },
};
export default function () {
  const response = http.get(`http://localhost:18081${entry.path}`, {
    cookies: { BILLIM_SESSION: entry.token }, redirects: 0, timeout: "10s",
    tags: { name: `GET /rentals ${testCase}` },
  });
  check(response, {
    "목록 200·응답·허용 행동 동일": (r) => {
      if (r.status !== 200) return false;
      try { return canonical(r.json()) === expected; }
      catch { return false; }
    },
  });
  sleep(0.2);
}
