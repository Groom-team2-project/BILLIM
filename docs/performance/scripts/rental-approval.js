import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const fixture = JSON.parse(open(`${__ENV.RENTAL_FIXTURE_DIR || "../../../performance/rental/local"}/approval-fixture.json`));
const unexpected = new Rate("unexpected_responses");
const approved = new Counter("approved_responses");
const conflicts = new Counter("date_conflict_responses");
const winnerTime = new Trend("approved_duration", true);
const conflictTime = new Trend("date_conflict_duration", true);

export const options = {
  vus: 1,
  iterations: 1,
  batch: 10,
  batchPerHost: 10,
  thresholds: { checks: ["rate==1"], unexpected_responses: ["rate==0"] },
};

export default function () {
  for (const wave of fixture.waves) {
    const requests = wave.map((entry) => ({
      method: "POST",
      url: `http://localhost:18081/api/v1/rentals/${entry.rentalId}/approve`,
      body: JSON.stringify({ expectedVersion: 0 }),
      params: {
        cookies: { BILLIM_SESSION: fixture.token },
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": fixture.csrf,
          "Idempotency-Key": entry.key },
        redirects: 0,
        timeout: "30s",
        tags: { name: "POST /api/v1/rentals/{id}/approve" },
      },
    }));
    const responses = http.batch(requests);
    let winners = 0;
    let rejected = 0;
    responses.forEach((response, index) => {
      let body;
      try { body = response.json(); } catch { body = {}; }
      const success = response.status === 200 && body.rental?.status === "APPROVED"
        && String(body.rental.id) === String(wave[index].rentalId);
      const conflict = response.status === 409 && body.code === "RENTAL_DATE_CONFLICT";
      unexpected.add(!success && !conflict);
      if (success) { winners++; approved.add(1); winnerTime.add(response.timings.duration); }
      if (conflict) { rejected++; conflicts.add(1); conflictTime.add(response.timings.duration); }
    });
    check(responses, {
      "동일 물건 승인 1건": () => winners === 1,
      "겹치는 요청 충돌 9건": () => rejected === 9,
    });
  }
}
