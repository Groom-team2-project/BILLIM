// 지도 역지오코딩이 켜진 상태의 주변 동네 응답. 외부 호출 비용만 보려 순차로 30회만 부른다
import http from 'k6/http';
import { Trend } from 'k6/metrics';
export const options = { vus: 1, iterations: 30, summaryTrendStats: ['avg','med','p(95)','max'] };
const USER = __ENV.USER_TOKEN;
const BASE = 'http://host.docker.internal:8080/api/v1';
const nearby = new Trend('nearby_region_on', true);
function uuid(){return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.random()*16|0;return (c==='x'?r:(r&0x3)|0x8).toString(16);});}
export function setup(){ const r=http.get(`${BASE}/auth/csrf`,{headers:{Cookie:`BILLIM_SESSION=${USER}`}}); return {csrf:r.json('csrfToken')}; }
export default function (data) {
  const r = http.post(`${BASE}/communities/nearby`,
    JSON.stringify({latitude:37.5+(Math.random()-0.5)*0.2, longitude:127.0+(Math.random()-0.5)*0.2, accuracyMeters:30}),
    {headers:{Cookie:`BILLIM_SESSION=${USER}`,'Content-Type':'application/json','X-CSRF-TOKEN':data.csrf,'Idempotency-Key':uuid()}});
  nearby.add(r.timings.duration);
  if (r.status !== 200) console.log(`status=${r.status} body=${r.body}`);
}
export function handleSummary(d){
  const m=d.metrics.nearby_region_on.values;
  return {'/work/k6-kakao.json': JSON.stringify({avg:m.avg,med:m.med,p95:m['p(95)'],max:m.max,failed:d.metrics.http_req_failed.values.rate},null,2)};
}
