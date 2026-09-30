import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/state/EmptyState";

type ErrorStateProps = {
  title?: string;
  /** 다시 시도 동작 — 없으면 새로고침 */
  onRetry?: () => void;
  extra?: ReactNode;
  children?: ReactNode;
};

/** 오류 상태 — 빈 상태에 경고 아이콘 + 다시 시도 (프로토타입 ErrorState) */
export function ErrorState({ title = "불러오지 못했어요", onRetry, extra, children }: ErrorStateProps) {
  return (
    <EmptyState
      icon="alert"
      role="alert"
      title={title}
      actions={
        <>
          <Button variant="secondary" icon="refresh" onClick={onRetry ?? (() => location.reload())}>
            다시 시도
          </Button>
          {extra}
        </>
      }
    >
      {children ?? "네트워크 연결을 확인한 뒤 다시 시도해 주세요. 계속되면 잠시 후에 열어 주세요."}
    </EmptyState>
  );
}
