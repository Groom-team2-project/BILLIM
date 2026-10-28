import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const fixture = JSON.parse(open(`${__ENV.RENTAL_FIXTURE_DIR || "../../../performance/rental/local"}/appointment-fixture.json`));
const unexpected = new Rate("unexpected_responses");
const successTime = new Trend("change_success_duration", true);
const conflictTime = new Trend("version_conflict_duration", true);
const accepted = new Counter("accept_successes");
const withdrawn = new Counter("withdraw_successes");

export const options = {
  vus: 1, iterations: 1, batch: 2, batchPerHost: 2,
  thresholds: { checks: ["rate==1"], unexpected_responses: ["rate==0"] },
};

export default function () {
  fixture.cases.forEach((entry, index) => {
    const actions = index % 2 === 0 ? ["accept", "withdraw"] : ["withdraw", "accept"];
    const responses = http.batch(actions.map((action) => {
      const user = fixture.users[action];
      return {
        method: "POST",
        url: `http://localhost:18081/api/v1/appointments/${entry.proposalId}/${action}`,
        body: JSON.stringify({ expectedVersion: 1 }),
        params: {
          cookies: { BILLIM_SESSION: user.token },
          headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": user.csrf,
            "Idempotency-Key": entry.keys[action] },
          timeout: "30s", redirects: 0,
          tags: { name: `POST /api/v1/appointments/{id}/${action}` },
        },
      };
    }));
    let successes = 0;
    let conflicts = 0;
    responses.forEach((response, i) => {
      let body;
      try { body = response.json(); } catch { body = {}; }
      const action = actions[i];
      const confirmed = action === "accept"
        ? String(body.confirmedProposal?.id) === String(entry.proposalId)
          && body.confirmedProposal?.status === "CONFIRMED"
        : body.confirmedProposal == null;
      const success = response.status === 200 && String(body.id) === String(entry.slotId)
        && body.version === 2 && body.pendingProposal == null && confirmed;
      const conflict = response.status === 409 && body.code === "VERSION_CONFLICT";
      unexpected.add(!success && !conflict);
      if (success) {
        successes++;
        successTime.add(response.timings.duration);
        if (action === "accept") accepted.add(1); else withdrawn.add(1);
      }
      if (conflict) { conflicts++; conflictTime.add(response.timings.duration); }
    });
    check(responses, {
      "수락·철회 중 한 건만 성공": () => successes === 1,
      "나머지 한 건은 최신 버전 충돌": () => conflicts === 1,
    });
  });
}
