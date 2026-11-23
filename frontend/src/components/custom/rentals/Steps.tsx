import type { RentalStatusView } from "@/components/custom/rentals/status";
import styles from "@/components/custom/rentals/Steps.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

const STEP_LABELS = ["요청", "승인", "대여 중", "반납"];
const STEP_KEYS = ["requested", "approved", "active", "returned"];
/** 연체는 대여 중과 같은 칸 — 별도 단계가 아니라 파생 표시 (정책 및 상태 §2) */
const STEP_INDEX: Partial<Record<RentalStatusView, number>> = {
  requested: 0,
  approved: 1,
  active: 2,
  overdue: 2,
  returned: 3,
};

/** 대여 진행 막대 — 지나온 칸은 각 단계의 상태색, 연체면 현재 칸 주홍 (DESIGN_SYSTEM.md §5-1) */
export function Steps({ status }: { status: RentalStatusView }) {
  const idx = STEP_INDEX[status];
  if (idx == null) return null;
  const stopped = status === "overdue";
  return (
    <ol className={c("steps")} aria-label={`진행 단계: ${stopped ? "반납 지연" : STEP_LABELS[idx]}`}>
      {STEP_LABELS.map((label, i) => {
        const state = i < idx ? "done" : i === idx ? "now" : "todo";
        const key = stopped && i === idx ? "overdue" : STEP_KEYS[i];
        return (
          <li key={label} className={c(`steps-item steps-item--${state}`)} data-step={key}>
            <i className={c("steps-bar")} />
            <span className={c("t-micro")}>{stopped && i === idx ? "반납 지연" : label}</span>
          </li>
        );
      })}
    </ol>
  );
}
