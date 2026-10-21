import http from "k6/http";
import { Counter } from "k6/metrics";

/**
 * 표시 이름 수정(PATCH /members/me)의 동시 요청 — 같은 expectedVersion으로 일괄 전송.
 * 응답시간이 아니라 응답 모양 집계 — 성공 1건 외 나머지가 받는 409의 종류.
 *   k6 run -e SESSION=... -e CSRF=... -e VERSION=1 member-rename-race.js
 */
const BASE = __ENV.BASE || "http://localhost:8080/api/v1";
const VERSION = Number(__ENV.VERSION);
// 저장된 이름과 같으면 변경 없음 처리로 UPDATE 미발생 — 실행마다 다른 이름
const RUN = __ENV.RUN || Date.now() % 100000;

const ok = new Counter("ok_200");
const conflictWithVersion = new Counter("conflict_with_currentVersion");
const conflictWithoutVersion = new Counter("conflict_without_currentVersion");
const other = new Counter("other_status");

export const options = { vus: 20, iterations: 20 };

export default function () {
  const res = http.patch(
    `${BASE}/members/me`,
    JSON.stringify({ displayName: `경합${RUN}-${__VU}`, expectedVersion: VERSION }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Requested-With": "XMLHttpRequest",
        "X-CSRF-TOKEN": __ENV.CSRF,
        Cookie: `BILLIM_SESSION=${__ENV.SESSION}`,
      },
    },
  );

  if (res.status === 200) { ok.add(1); return; }
  if (res.status === 409) {
    const body = res.json();
    (body && body.currentVersion !== undefined ? conflictWithVersion : conflictWithoutVersion).add(1);
    return;
  }
  other.add(1);
  console.log(`예상 외 응답 ${res.status}: ${res.body}`);
}
