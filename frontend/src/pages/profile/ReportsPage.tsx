import { useEffect, useState } from "react";
import { ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ApiError } from "@/api/client";
import { type ApiReport, type ReportReasonCode, getMyReports } from "@/api/reports";
import styles from "@/pages/profile/ReportsPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 신고 사유 6종 문구 — 프로토타입 표현 기준 (명세 0.5 reasonCode) */
const REASON_LABEL: Record<ReportReasonCode, string> = {
  MONEY_REQUEST: "돈이나 보증금을 요구했어요",
  ITEM_MISMATCH: "물건 설명과 달라요",
  NO_SHOW: "약속을 반복해서 어겨요",
  ABUSE: "욕설·비방을 했어요",
  FRAUD: "사기가 의심돼요",
  OTHER: "기타",
};

type List =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; rows: ApiReport[] };

function dateLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getFullYear()}년 ${at.getMonth() + 1}월 ${at.getDate()}일`;
}

export function ReportsPage() {
  const [list, setList] = useState<List>({ state: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let alive = true;
    getMyReports()
      .then((page) => alive && setList({ state: "ok", rows: page.items }))
      .catch((e: unknown) => {
        if (!alive) return;
        setList({
          state: "error",
          message: e instanceof ApiError ? e.message : "신고 내역을 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [retry]);

  return (
    <div className={c("reports page-window")}>
      <PageHeader title="신고 내역" backTo="/profile" />
      <p className={c("t-body reports-muted")}>
        내가 접수한 신고예요. 운영팀이 확인한 뒤 처리 결과를 알림으로 알려줘요.
      </p>

      {list.state === "loading" && (
        <Loading label="신고 내역을 불러오는 중">
          <div className={c("reports-card")}>
            <SkeletonItem layout="row" />
            <SkeletonItem layout="row" />
          </div>
        </Loading>
      )}

      {list.state === "error" && (
        <ErrorState
          onRetry={() => {
            setList({ state: "loading" });
            setRetry((n) => n + 1);
          }}
        >
          {list.message}
        </ErrorState>
      )}

      {list.state === "ok" && (
        <div className={c("reports-card")}>
          {list.rows.length === 0 ? (
            <p className={c("t-caption reports-muted")}>접수한 신고가 없어요.</p>
          ) : (
            list.rows.map((report) => {
              const done = report.status === "RESOLVED";
              return (
                <div key={report.id} className={c("reports-row")}>
                  <div className={c("reports-row-head")}>
                    <span className={c("t-body reports-row-name")}>
                      {report.targetMember.displayName} 님 신고
                    </span>
                    <span
                      className={c(
                        `reports-pill ${done ? "reports-pill--done" : "reports-pill--pending"}`,
                      )}
                    >
                      {done ? "처리 완료" : "확인 중"}
                    </span>
                  </div>
                  <div className={c("t-caption reports-muted")}>
                    {report.detail?.trim() || REASON_LABEL[report.reasonCode]}
                  </div>
                  <div className={c("t-caption reports-muted")}>{dateLabel(report.createdAt)}</div>
                  {done && report.publicReason ? (
                    <div className={c("t-caption reports-muted")}>처리 결과 · {report.publicReason}</div>
                  ) : null}
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
