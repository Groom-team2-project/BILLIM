import { api } from "@/api/client";

export type ReportReasonCode = "MONEY_REQUEST" | "ITEM_MISMATCH" | "NO_SHOW" | "ABUSE" | "FRAUD" | "OTHER";

export function reportMessage(messageId: string, reasonCode: ReportReasonCode, detail?: string): Promise<unknown> {
  return api<unknown>("/reports", {
    method: "POST",
    json: { targetType: "MESSAGE", targetId: messageId, reasonCode, detail },
  });
}
