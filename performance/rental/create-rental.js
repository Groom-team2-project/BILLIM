import http from "k6/http";
import { check, fail, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const fixture = JSON.parse(open("./local/create-fixture.json"));
const phase = __ENV.PHASE || "baseline";
const unexpected = new Rate("unexpected_responses");
const replayTime = new Trend("create_replay_duration", true);
const rejectedResponses = new Counter("rejected_responses");
const notFoundResponses = new Counter("create_replay_not_found");
const createdResponses = new Counter("create_replay_successes");
const changedBodyConflicts = new Counter("create_changed_body_conflicts");
let reportedFailure = false;

export const options = phase === "replay"
  ? { vus: 1, iterations: 1, batch: 10, batchPerHost: 10,
      thresholds: { checks: ["rate==1"], unexpected_responses: ["rate==0"] } }
  : { vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || "3m",
      thresholds: { checks: ["rate==1"], http_req_failed: ["rate==0"], unexpected_responses: ["rate==0"] } };

function request(entry, user, changed = false) {
  return {
    method: "POST", url: "http://localhost:18081/api/v1/rentals",
    body: JSON.stringify({ itemId: String(entry.itemId), startDate: fixture.startDate,
      endDate: changed ? fixture.changedEndDate : fixture.endDate }),
    params: {
      cookies: { BILLIM_SESSION: user.token },
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": user.csrf, "Idempotency-Key": entry.key },
      redirects: 0, timeout: "15s", tags: { name: "POST /api/v1/rentals", phase },
    },
  };
}

function bodyOf(response) { try { return response.json(); } catch { return {}; } }
function valid(response, entry, user) {
  const body = bodyOf(response);
  return response.status === 201 && String(body.rental?.itemId) === String(entry.itemId)
    && String(body.rental?.borrower?.id) === String(user.memberId)
    && body.rental?.status === "REQUESTED" && body.rental?.version === 0;
}
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value !== null && typeof value === "object") {
    return Object.keys(value).sort().reduce((result, key) => { result[key] = canonical(value[key]); return result; }, {});
  }
  return value;
}

export default function () {
  if (phase !== "replay") {
    const user = fixture.users[__VU - 1];
    const entry = fixture.phases[phase]?.[__VU - 1]?.[__ITER];
    if (!entry || !user) fail("요청별 고유 테스트 데이터 부족");
    const req = request(entry, user);
    const response = http.post(req.url, req.body, req.params);
    const ok = valid(response, entry, user);
    if (!ok) {
      const code = bodyOf(response).code || "INVALID_RESPONSE";
      rejectedResponses.add(1, { code, status: String(response.status), vu: String(__VU) });
      if (!reportedFailure) console.warn(`실패 분류: VU=${__VU}, status=${response.status}, code=${code}`);
      reportedFailure = true;
    }
    unexpected.add(!ok);
    check(response, { "대여 생성 201·대상·요청자·초기 상태 일치": () => ok });
    sleep(1);
    return;
  }
  fixture.phases.replay.forEach((entry, i) => {
    const user = fixture.users[i];
    const responses = http.batch(Array.from({ length: 10 }, () => request(entry, user)));
    const firstSuccess = responses.find((response) => valid(response, entry, user));
    const expected = JSON.stringify(canonical(bodyOf(firstSuccess)));
    const results = responses.map((response) => {
      const ok = valid(response, entry, user) && JSON.stringify(canonical(bodyOf(response))) === expected;
      if (response.status === 404) notFoundResponses.add(1);
      if (valid(response, entry, user)) createdResponses.add(1);
      unexpected.add(!ok);
      return ok;
    });
    const failedResponse = responses.find((response) => !valid(response, entry, user));
    if (failedResponse) console.warn(`생성 재전송 실패 분류: status=${failedResponse.status}, code=${bodyOf(failedResponse).code || "INVALID_RESPONSE"}`);
    check(responses, { "생성 동시 재전송 10건 동일 응답": () => results.every(Boolean) });
    const req = request(entry, user);
    const replay = http.post(req.url, req.body, req.params);
    replayTime.add(replay.timings.duration);
    const same = valid(replay, entry, user) && JSON.stringify(canonical(bodyOf(replay))) === expected;
    if (replay.status === 404) notFoundResponses.add(1);
    if (valid(replay, entry, user)) createdResponses.add(1);
    unexpected.add(!same);
    check(replay, { "생성 완료 후 재전송 응답 일치": () => same });
    const changed = request(entry, user, true);
    const rejection = http.post(changed.url, changed.body, changed.params);
    const rejected = rejection.status === 409 && bodyOf(rejection).code === "IDEMPOTENCY_KEY_REUSED";
    if (rejected) changedBodyConflicts.add(1);
    unexpected.add(!rejected);
    check(rejection, { "생성 키 다른 본문 재사용 거부": () => rejected });
  });
}
