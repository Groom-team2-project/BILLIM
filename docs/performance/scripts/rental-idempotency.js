import http from "k6/http";
import { check } from "k6";
import { Rate, Trend } from "k6/metrics";

const fixture = JSON.parse(open(`${__ENV.RENTAL_FIXTURE_DIR || "../../../performance/rental/local"}/approval-fixture.json`));
const unexpected = new Rate("unexpected_responses");
const replayTime = new Trend("replay_duration", true);
const reuseTime = new Trend("key_reuse_duration", true);

export const options = {
  vus: 1,
  iterations: 1,
  batch: 10,
  batchPerHost: 10,
  thresholds: { checks: ["rate==1"], unexpected_responses: ["rate==0"] },
};

function request(entry, version, phase) {
  return {
    method: "POST",
    url: `http://localhost:18081/api/v1/rentals/${entry.rentalId}/approve`,
    body: JSON.stringify({ expectedVersion: version }),
    params: {
      cookies: { BILLIM_SESSION: fixture.token },
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": fixture.csrf,
        "Idempotency-Key": entry.key },
      redirects: 0,
      timeout: "30s",
      tags: { name: "POST /api/v1/rentals/{id}/approve", phase },
    },
  };
}

function bodyOf(response) {
  try { return response.json(); } catch { return {}; }
}

// JSON 객체의 필드 순서는 응답 의미와 무관하므로 정렬 후 비교
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value !== null && typeof value === "object") {
    return Object.keys(value).sort().reduce((result, key) => {
      result[key] = canonical(value[key]);
      return result;
    }, {});
  }
  return value;
}

export default function () {
  for (const wave of fixture.waves) {
    const entry = wave[0];
    const responses = http.batch(Array.from({ length: 10 }, () => request(entry, 0, "concurrent")));
    const first = bodyOf(responses[0]);
    const expected = JSON.stringify(canonical(first));
    const results = responses.map((response) => {
      const body = bodyOf(response);
      const valid = response.status === 200 && body.rental?.status === "APPROVED"
        && String(body.rental.id) === String(entry.rentalId) && body.rental.version === 1
        && JSON.stringify(canonical(body)) === expected;
      unexpected.add(!valid);
      return valid;
    });
    check(responses, { "동시 재전송 10건 모두 동일 성공 응답": () => results.every(Boolean) });

    const replayRequest = request(entry, 0, "replay");
    const replay = http.post(replayRequest.url, replayRequest.body, replayRequest.params);
    replayTime.add(replay.timings.duration);
    const replayValid = replay.status === 200 && first.rental?.status === "APPROVED"
      && JSON.stringify(canonical(bodyOf(replay))) === expected;
    unexpected.add(!replayValid);
    check(replay, { "완료 후 재전송 응답 일치": () => replayValid });

    const changedRequest = request(entry, 1, "changed_body");
    const changed = http.post(changedRequest.url, changedRequest.body, changedRequest.params);
    reuseTime.add(changed.timings.duration);
    const rejected = changed.status === 409 && bodyOf(changed).code === "IDEMPOTENCY_KEY_REUSED";
    unexpected.add(!rejected);
    check(changed, { "동일 키 다른 본문 거부": () => rejected });
  }
}
