import styles from "@/pages/profile/ReportsPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 프로토타입 목데이터 — 내가 접수한 신고. API 연동 시 교체 */
const REPORTS = [
  { name: "정우", reason: "돈이나 보증금을 요구했어요", date: "2026년 9월 15일", done: false },
  { name: "도윤", reason: "약속을 반복해서 어겨요", date: "2026년 8월 20일", done: true },
];

export function ReportsPage() {
  return (
    <div className={c("reports page-window")}>
      <PageHeader title="신고 내역" backTo="/profile" />
      <p className={c("t-body reports-muted")}>
        내가 접수한 신고예요. 운영팀이 확인한 뒤 처리 결과를 알림으로 알려줘요.
      </p>
      <div className={c("reports-card")}>
        {REPORTS.map((r) => (
          <div key={r.name} className={c("reports-row")}>
            <div className={c("reports-row-head")}>
              <span className={c("t-body reports-row-name")}>{r.name} 님 신고</span>
              <span className={c(`reports-pill ${r.done ? "reports-pill--done" : "reports-pill--pending"}`)}>
                {r.done ? "처리 완료" : "확인 중"}
              </span>
            </div>
            <div className={c("t-caption reports-muted")}>{r.reason}</div>
            <div className={c("t-caption reports-muted")}>{r.date}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
