// 실제 배포 서버 요청 없이 요청 상한·조회 경로·실패 즉시 중단 검증.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(`${__dirname}/deploy-read-smoke.js`, 'utf8')
  .replace(/^import .*;\r?\n/gm, '')
  .replace('export const options =', 'globalThis.options =')
  .replace('export default function ()', 'globalThis.iteration = function ()');

function load(config, status = 200, env = {}) {
  const calls = [];
  class Metric { add() {} }
  const context = {
    __ENV: { BASE_URL: 'https://example.invalid', ...env },
    open: () => JSON.stringify(config), Rate: Metric, Trend: Metric,
    sleep: seconds => assert.equal(seconds, 1),
    check: (value, checks) => Object.values(checks).every(check => check(value)),
    exec: { scenario: { iterationInTest: 0 }, test: { abort: message => { throw new Error(message); } } },
    http: { get: (url, options) => {
      calls.push({ url, options });
      return { status, timings: { duration: 1 }, json: () => ({ items: [], unreadCount: 0 }) };
    } },
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  return { context, calls };
}

for (const roomId of [undefined, '123']) {
  const { context, calls } = load({ sessionToken: 'test-only', roomId });
  assert.equal(context.options.scenarios.gentle_reads.vus, 1);
  assert.equal(context.options.scenarios.gentle_reads.iterations, 24);
  for (let i = 0; i < 24; i++) {
    context.exec.scenario.iterationInTest = i;
    context.iteration();
  }
  assert.equal(calls.length, 24);
  assert.equal(new Set(calls.map(call => call.url)).size, roomId ? 5 : 4);
  assert.ok(calls.every(call => call.options.redirects === 0 && call.options.timeout === '5s'));
}
for (const status of [0, 302, 401, 403, 429, 500]) {
  const { context, calls } = load({ sessionToken: 'test-only' }, status);
  assert.throws(() => context.iteration(), /response validation failed/);
  assert.equal(calls.length, 1);
}
for (const count of ['0', '61', '2.5', 'NaN']) {
  assert.throws(() => load({ sessionToken: 'test-only' }, 200, { REQUEST_COUNT: count }), /Request count/);
}
assert.throws(() => load({ sessionToken: 'bad;cookie' }), /token/);
assert.throws(() => load({ sessionToken: 'test-only', roomId: '../123' }), /room ID/);
assert.throws(() => load({ sessionToken: 'test-only' }, 200, { BASE_URL: 'https://example.invalid/redirect' }), /origin/);
console.log('배포 조회 테스트의 요청 제한·조회 경로·오류 중단 검사 통과');
