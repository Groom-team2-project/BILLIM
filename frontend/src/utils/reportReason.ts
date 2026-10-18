import type { ReportReasonCode } from "@/api/reports";

/** useUi().report가 돌려주는 한글 사유 → API reasonCode (명세 0.5) */
const CODES: Record<string, ReportReasonCode> = {
  "돈이나 보증금을 요구했어요": "MONEY_REQUEST",
  "물건 상태가 설명과 크게 달라요": "ITEM_MISMATCH",
  "약속을 반복해서 어겨요": "NO_SHOW",
  "무례하거나 위협적인 언행을 했어요": "ABUSE",
  "사기가 의심돼요": "FRAUD",
};

/** 고정 사유 외는 기타 — 사용자 입력 문구 */
export function toReasonCode(reason: string): ReportReasonCode {
  return CODES[reason] ?? "OTHER";
}

/** 기타만 입력 문구를 detail로 전달 */
export function toReasonDetail(reason: string): string | undefined {
  if (CODES[reason]) return undefined;
  const trimmed = reason.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}
