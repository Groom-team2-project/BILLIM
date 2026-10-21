// 측정 하한. k6 컨테이너 → 호스트 왕복과 서블릿 통과 비용만 본다 (DB·인증 없음)
import http from 'k6/http';
import { Trend } from 'k6/metrics';
export const options = { vus: Number(__ENV.VUS||1), duration: __ENV.DURATION||'15s', summaryTrendStats:['avg','p(95)','max'] };
const health = new Trend('health', true);
export default function () {
  const r = http.get('http://host.docker.internal:8080/actuator/health');
  health.add(r.timings.duration);
}
export function handleSummary(d) {
  const m = d.metrics.health.values;
  return { '/work/k6-floor.json': JSON.stringify({ avg:m.avg, p95:m['p(95)'], max:m.max, rps:d.metrics.http_reqs.values.rate }, null, 2) };
}
