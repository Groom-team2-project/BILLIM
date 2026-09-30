import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/state/State.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type EmptyStateProps = {
  icon?: string;
  title: string;
  role?: "status" | "alert";
  actions?: ReactNode;
  children?: ReactNode;
};

/** 빈 상태 — 아이콘 + 제목 + 설명 + 행동 (프로토타입 nb-state) */
export function EmptyState({ icon = "search", title, role, actions, children }: EmptyStateProps) {
  return (
    <div className={c("state")} role={role}>
      <div className={c("state-mark")}>
        <Icon name={icon} size={30} />
      </div>
      <div className={c("state-title")}>{title}</div>
      {children ? <div className={c("state-text")}>{children}</div> : null}
      {actions ? <div className={c("state-actions")}>{actions}</div> : null}
    </div>
  );
}
