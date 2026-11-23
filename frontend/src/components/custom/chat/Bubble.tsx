import type { ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/custom/chat/Bubble.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 날짜 구분선 */
export function DateDivider({ children }: { children: ReactNode }) {
  return <div className={c("msg-date")}>{children}</div>;
}

/** 거래 사건 시스템 메시지 */
export function SystemMessage({ icon, children }: { icon?: string; children: ReactNode }) {
  return (
    <div className={c("msg-system")}>
      {icon ? <Icon name={icon} size={14} /> : null}
      {children}
    </div>
  );
}

type BubbleProps = {
  me?: boolean;
  time?: string;
  read?: boolean;
  failed?: boolean;
  children: ReactNode;
};

export function Bubble({ me, time, read, failed, children }: BubbleProps) {
  return (
    <div className={c(`msg${me ? " msg--me" : ""}${failed ? " msg--failed" : ""}`)}>
      <div className={c("msg-bubble")}>{children}</div>
      <span className={c("msg-time")}>
        {failed ? (
          <>
            <Icon name="alert" size={13} /> 전송 실패
          </>
        ) : (
          `${read ? "읽음 · " : ""}${time ?? ""}`
        )}
      </span>
    </div>
  );
}
