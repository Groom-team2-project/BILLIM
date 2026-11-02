import type { ApiMySanction } from "@/api/reports";

/** 긴 운영 사유는 팝업에서 요약하고 전체 문장은 받은 운영 조치 화면에 둔다. */
export function warningSummary(reason: string): string {
  const characters = Array.from(reason);
  return characters.length > 160 ? `${characters.slice(0, 160).join("")}…` : reason;
}

function key(memberId: string, actionId: string): string {
  return `billim-warning-confirmed:${memberId}:${actionId}`;
}

/** 같은 브라우저의 계정·조치별 확인 기록. 저장소를 못 읽으면 다시 안내한다. */
export function nextWarning(
  rows: ApiMySanction[], memberId: string, shown: Set<string>,
  storage: Pick<Storage, "getItem"> | null,
): ApiMySanction | undefined {
  return rows.find((row) => {
    if (row.action !== "WARN" || shown.has(row.id)) return false;
    try {
      return storage?.getItem(key(memberId, row.id)) !== "1";
    } catch {
      return true;
    }
  });
}

export function confirmWarning(memberId: string, actionId: string, storage: Pick<Storage, "setItem"> | null): void {
  try {
    storage?.setItem(key(memberId, actionId), "1");
  } catch {
    // 저장이 막히면 다음 로그인 때 다시 안내한다.
  }
}
