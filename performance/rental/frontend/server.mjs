/** 기존 커밋과 작업 트리를 같은 브라우저 조건에서 비교하는 로컬 전용 서버. */
import { createServer } from '../../../frontend/node_modules/vite/dist/node/index.js';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../../../', import.meta.url));
const baseline = process.env.RENTAL_BASE_REF ?? '42efe8d';
const before = execFileSync('git', ['show', `${baseline}:frontend/src/pages/rentals/RentalsPage.tsx`], { cwd: root, encoding: 'utf8' });
const beforeId = path.join(root, 'frontend/src/pages/rentals/__rental-before.tsx');
const entry = path.join(root, 'performance/rental/frontend/fixture.tsx');
const server = await createServer({
  root: path.join(root, 'frontend'),
  configFile: path.join(root, 'frontend/vite.config.ts'),
  server: { host: '127.0.0.1', port: 5179, strictPort: true, fs: { allow: [root] } },
  plugins: [{
    name: 'rental-performance',
    resolveId(id) { if (id === 'virtual:rental-before' || id === beforeId || id === '/src/pages/rentals/__rental-before.tsx') return beforeId; },
    load(id) { if (id === beforeId) return before; },
    configureServer(s) {
      s.middlewares.use(async (req, res, next) => {
        if (!req.url?.startsWith('/__rental-performance')) return next();
        const html = `<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>내 대여 성능 검증</title><div id="root"></div><script type="module" src="/@fs/${entry}"></script></html>`;
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        res.end(await s.transformIndexHtml(req.url, html));
      });
    },
  }],
});
await server.listen();
console.log(`내 대여 성능 검증: http://127.0.0.1:5179/__rental-performance?variant=before&count=10 (기준 ${baseline})`);
