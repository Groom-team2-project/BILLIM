import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/alert/Alert.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type AlertProps = {
  tone?: "info" | "warning" | "danger";
  title?: string;
  /** 하단 행동 버튼 (프로토타입 nb-alert__actions) */
  actions?: ReactNode;
  children?: ReactNode;
};

/** 안내·경고 박스 (DESIGN_SYSTEM.md §6 피드백) */
export function Alert({ tone = "info", title, actions, children }: AlertProps) {
  return (
    <div className={c(`alert alert--${tone}`)} role={tone === "info" ? "status" : "alert"}>
      <Icon name={tone === "info" ? "info" : "alert"} size={20} />
      <div className={c("alert-body")}>
        {title ? <div className={c("alert-title")}>{title}</div> : null}
        {children ? <div className={c("alert-text")}>{children}</div> : null}
        {actions ? <div className={c("alert-actions")}>{actions}</div> : null}
      </div>
    </div>
  );
}
