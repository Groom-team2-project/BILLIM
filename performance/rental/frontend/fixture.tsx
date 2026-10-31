/** 합성 응답 전용. 실제 계정·서버 호출 없이 화면의 요청·렌더링 비용만 측정. */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom';
import { UiProvider } from '../../../frontend/src/components/overlay/UiProvider';
import '../../../frontend/src/styles/tokens.css';
import '../../../frontend/src/styles/global.css';

const params = new URLSearchParams(location.search);
const variant = params.get('variant') ?? 'after';
const count = Number(params.get('count') ?? 10);
const delay = Number(params.get('delay') ?? 100);
const scenario = params.get('scenario') ?? 'normal';
if (![10, 100, 1000].includes(count) || delay < 0 || delay > 1000) throw new Error('잘못된 측정 조건');
const person = (id: string) => ({ id, displayName: id === '1' ? '내 계정' : '합성 이웃', joinedAt: '2026-01-01' });
const statuses = ['APPROVED', 'REJECTED', 'ACTIVE', 'REQUESTED', 'RETURNED', 'CANCELED', 'EXPIRED'];
const records = Array.from({ length: count }, (_, i) => ({
  id: String(10000 + i), itemId: String(20000 + i), itemTitle: `합성 거래 ${i + 1}`,
  owner: person(i % 2 ? '1' : '2'), borrower: person(i % 2 ? '2' : '1'),
  status: statuses[i % statuses.length], overdue: i % 7 === 2,
  startDate: '2026-10-31', endDate: '2026-11-02', dDay: 2, placeName: '정문',
  version: 1, allowedActions: ['CANCEL', 'APPROVE', 'REJECT', 'RETURN', 'HANDOVER'], createdAt: new Date(Date.UTC(2026, 9, 31, 0, 0, count - i)).toISOString(),
}));
const roleItems = (role: string) => records.filter(r => (role === 'OWNER' ? r.owner.id : r.borrower.id) === '1');
const counts = (role: string) => Object.fromEntries(['requested','approved','active','overdue','returned','rejected','canceled','expired'].map(status => [status, roleItems(role).filter(r => status === 'overdue' ? r.status === 'ACTIVE' && r.overdue : r.status.toLowerCase() === status).length]));
let active = 0, maxActive = 0, completed = 0, errors = 0;
let failed = false, conflicted = false, refreshFailed = false;
const requests: { path: string; startedMs: number; endedMs?: number; status?: number }[] = [];
let started = 0;
const detail = (id: string) => {
  const r = records.find(r => r.id === id)!;
  return { rental: r, reason: ['REJECTED','CANCELED'].includes(r.status) ? '합성 거래 사유' : undefined, chatRoomId: '99',
    appointments: [{ kind: r.status === 'ACTIVE' ? 'RETURN' : 'PICKUP', confirmedProposal: { scheduledAt: '2026-10-31T10:00:00Z', place: { name: '확정 약속 장소' } } }] };
};
window.fetch = async (input, init = {}) => {
  const url = new URL(String(input), location.origin);
  if (!url.pathname.startsWith('/api/v1/')) throw new Error('실제 네트워크 요청 차단');
  const request = { path: url.pathname + url.search, startedMs: performance.now() - started };
  requests.push(request); active++; maxActive = Math.max(maxActive, active);
  await new Promise(resolve => setTimeout(resolve, delay));
  let body: unknown, status = 200;
  if (url.pathname.endsWith('/auth/csrf')) body = { csrfToken: 'synthetic' };
  else if (url.pathname.endsWith('/members/me')) body = { member: person('1') };
  else if (url.pathname.endsWith('/rentals/summary')) {
    body = { borrowed: counts('BORROWER'), lent: counts('OWNER'), nextRentals: [] };
    if (scenario === 'summary-fail' && !failed) { failed = true; status = 503; body = { message: '합성 건수 오류' }; }
  }
  else if (url.pathname.endsWith('/rentals')) {
    const role = url.searchParams.get('role')!;
    let items = roleItems(role);
    const filter = url.searchParams.get('status');
    if (filter) items = items.filter(r => r.status === filter);
    if (url.searchParams.get('overdueOnly') === 'true') items = items.filter(r => r.status === 'ACTIVE' && r.overdue);
    const page = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 20);
    body = { items: items.slice(page * size, (page + 1) * size), page, size, totalElements: items.length, hasNext: (page + 1) * size < items.length };
    if (scenario === 'page-fail' && page === 1 && !failed) { failed = true; status = 503; body = { message: '합성 다음 페이지 오류' }; }
  } else {
    const match = url.pathname.match(/\/rentals\/(\d+)(?:\/(approve|reject|cancel|handover|return))?$/);
    if (!match || !records.some(r => r.id === match[1])) { status = 404; body = { message: '합성 거래 없음' }; }
    else if (match[2]) {
      const r = records.find(r => r.id === match[1])!;
      const sent = JSON.parse(init.body as string);
      if (!conflicted) { conflicted = true; r.version = 2; status = 409; body = { code: 'VERSION_CONFLICT', message: '최신 상태 확인 필요' }; }
      else if (sent.expectedVersion !== r.version) { status = 409; body = { code: 'VERSION_CONFLICT', message: '이전 버전 요청' }; }
      else { r.version++; r.status = 'CANCELED'; body = detail(r.id); }
    } else if (scenario === 'target-fail' && !failed && match[1] === params.get('rentalId')) { failed = true; status = 503; body = { message: '합성 알림 거래 오류' }; }
    else if (scenario === 'detail-fail' && !failed && match[1] === '10000') { failed = true; status = 503; body = { message: '합성 상세 오류' }; }
    else if (scenario === 'refresh-fail' && conflicted && !refreshFailed) { refreshFailed = true; status = 503; body = { message: '합성 재조회 오류' }; }
    else body = detail(match[1]);
  }
  active--; completed++; if (status >= 400) errors++;
  Object.assign(request, { endedMs: performance.now() - started, status });
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
};

// @ts-expect-error 기준 커밋의 화면을 로컬 서버가 제공하는 가상 모듈로 로드.
const Page = variant === 'before' ? (await import('virtual:rental-before')).RentalsPage : (await import('../../../frontend/src/pages/rentals/RentalsPage')).RentalsPage;
function Chat() { return <h1>채팅 연결 확인 {useLocation().pathname}</h1>; }
createRoot(document.getElementById('root')!).render(<><h1>합성 응답 · {variant} · 전체 {count}건 · 응답당 {delay}ms</h1><output data-testid="measurement" style={{ display: 'block', maxHeight: 'calc(var(--space-8) * 4)', overflow: 'auto', overflowWrap: 'anywhere' }}>측정 중</output><UiProvider><BrowserRouter><Routes><Route path="/__rental-performance" element={<Page />} /><Route path="/chat/:id" element={<Chat />} /></Routes></BrowserRouter></UiProvider></>);
started = performance.now();
let firstCardMs: number | undefined, done = false;
const observer = new MutationObserver(() => {
  if (!firstCardMs && document.querySelector('article')) firstCardMs = performance.now() - started;
  if (done || !firstCardMs || active) return;
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (done || active) return;
    done = true;
    document.querySelector('[data-testid="measurement"]')!.textContent = JSON.stringify({ variant, count, delay, firstCardMs, readyMs: performance.now() - started, requests: requests.length, completed, errors, maxActive, cards: document.querySelectorAll('article').length, paths: requests });
  }));
});
observer.observe(document.getElementById('root')!, { childList: true, subtree: true });
