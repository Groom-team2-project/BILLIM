import type { ReactNode } from "react";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { Photo } from "@/components/custom/items/ItemCard";
import { fmtDate, type MonthDay } from "@/utils/date";
import type { CategoryId } from "@/data/items";
import type { RentalStatusView } from "@/components/custom/rentals/status";
import styles from "@/components/custom/chat/ContextBar.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type ContextRental = {
  title: string;
  cat: CategoryId;
  status: RentalStatusView;
  start: MonthDay;
  end: MonthDay;
  place: string;
};

/** 채팅방 상단 고정 — 어떤 거래의 대화인지 (DESIGN_SYSTEM.md §6 채팅) */
export function ContextBar({ rental, action }: { rental: ContextRental; action?: ReactNode }) {
  return (
    <div className={c("context-bar")}>
      <Photo cat={rental.cat} iconSize={18} className={c("context-bar-thumb")} />
      <div className={c("context-bar-body")}>
        <div className={c("context-bar-title")}>
          <strong className={c("t-label")}>{rental.title}</strong>
          <StatusBadge status={rental.status} />
        </div>
        <span className={c("t-caption context-bar-muted")}>
          {fmtDate(rental.start)} – {fmtDate(rental.end)} · {rental.place}
        </span>
      </div>
      {action ?? null}
    </div>
  );
}
