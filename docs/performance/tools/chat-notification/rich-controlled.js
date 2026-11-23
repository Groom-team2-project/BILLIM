import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const fixture = JSON.parse(open('./local/fixtures-rich.json'));
const token = fixture.pairs[0].owner.token;
const warm = __ENV.WARM === 'true';
const size = Number(__ENV.PAGE_SIZE);
const failures = new Rate('app_failures');
export const options = {
  scenarios: { selected: warm
    ? { executor: 'shared-iterations', vus: 4, iterations: 300, maxDuration: '5m' }
    : { executor: 'constant-vus', vus: 4, duration: '30s' } },
  thresholds: { app_failures: ['rate==0'], http_req_failed: ['rate==0'] },
};
export default function () {
  const response = http.get(`http://backend:8080/api/v1/chat/rooms?filter=ALL&page=0&size=${size}`,
    { headers: { Cookie: `BILLIM_SESSION=${token}` } });
  const ok = check(response, {
    'valid room list': r => r.status === 200 && r.json('items').length === size,
  });
  failures.add(!ok);
  if (!warm) sleep(0.4);
}
