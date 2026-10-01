import { useSyncExternalStore } from "react";
import { INITIAL_NOTICES, INITIAL_REPORTS, type Notice, type Report } from "@/data/admin";

/** 화면 간 공유 상태 — 신고 처리·공지 등록이 대시보드 수치에 반영 */
let reports: Report[] = [...INITIAL_REPORTS];

let notices: Notice[] = [...INITIAL_NOTICES];

const subs = new Set<() => void>();

function emit() {
  for (const f of subs) f();
}

function subscribe(f: () => void) {
  subs.add(f);
  return () => {
    subs.delete(f);
  };
}

export function useReports(): Report[] {
  return useSyncExternalStore(subscribe, () => reports);
}

export function useNotices(): Notice[] {
  return useSyncExternalStore(subscribe, () => notices);
}

export function resolveReport(id: number, action: string) {
  reports = reports.map((r) => (r.id === id ? { ...r, status: "done" as const, action } : r));
  emit();
}

export function addNotice(title: string) {
  notices = [{ id: Date.now(), title, date: "오늘", pinned: false }, ...notices];
  emit();
}

export function removeNotice(id: number) {
  notices = notices.filter((n) => n.id !== id);
  emit();
}
