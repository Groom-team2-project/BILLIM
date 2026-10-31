import { test } from "node:test";
import assert from "node:assert/strict";
import type { ApiMySanction } from "../src/api/reports.ts";
import { confirmWarning, nextWarning, warningSummary } from "../src/utils/warning-notice.ts";

const warning = (id: string): ApiMySanction => ({
  id, action: "WARN", publicReason: "규칙 위반", decidedAt: "2026-11-01T00:00:00Z",
  targetType: "MEMBER", isEffective: false,
});

test("확인한 경고는 같은 계정에서 재로그인해도 건너뛰고 새 경고는 보여준다", () => {
  const values = new Map<string, string>();
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
  };
  const rows = [warning("new"), warning("old")];

  confirmWarning("member-a", "old", storage);
  assert.equal(nextWarning(rows, "member-a", new Set(), storage)?.id, "new");
  confirmWarning("member-a", "new", storage);
  assert.equal(nextWarning(rows, "member-a", new Set(), storage), undefined);
  assert.equal(nextWarning(rows, "member-b", new Set(), storage)?.id, "new");
});

test("경고가 아닌 조치는 건너뛰고 저장소 접근이 실패해도 안내를 시도한다", () => {
  const rows = [{ ...warning("ban"), action: "BAN" as const }, warning("warn")];
  const storage = { getItem: (): string => { throw new Error("blocked"); } };
  assert.equal(nextWarning(rows, "member-a", new Set(), storage)?.id, "warn");
  assert.equal(nextWarning(rows, "member-a", new Set(["warn"]), storage), undefined);
});

test("긴 사유는 팝업 크기에 맞게 줄이고 전체 내용은 조치 화면에 남긴다", () => {
  assert.equal(warningSummary("짧은 사유"), "짧은 사유");
  assert.equal(warningSummary("가".repeat(161)), `${"가".repeat(160)}…`);
});
