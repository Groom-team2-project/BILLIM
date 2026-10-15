import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { useUi } from "@/components/overlay/UiContext";
import { ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ActionModal } from "@/components/custom/admin/ActionModal";
import { ApiError } from "@/api/client";
import {
  type ApiAdminReport,
  type ReportActionType,
  type ReportReasonCode,
  getAdminReports,
  resolveReport,
} from "@/api/reports";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

const FILTERS = [
  { value: undefined, label: "전체" },
  { value: "PENDING", label: "확인 중" },
  { value: "RESOLVED", label: "처리 완료" },
] as const;

type Filter = (typeof FILTERS)[number]["value"];

/** 신고 사유 6종 표시 문구 (명세 0.5 reasonCode) */
const REASON_LABEL: Record<ReportReasonCode, string> = {
  MONEY_REQUEST: "금전 요구",
  ITEM_MISMATCH: "물건 설명 불일치",
  NO_SHOW: "약속 불이행",
  ABUSE: "욕설·비방",
  FRAUD: "사기 의심",
  OTHER: "기타",
};

const ACTION_LABEL: Record<ReportActionType, string> = {
  WARN: "경고",
  SUSPEND_7D: "일시정지 7일",
  SUSPEND_30D: "일시정지 30일",
  BAN: "영구정지",
  DISMISS: "반려",
};

type List =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; rows: ApiAdminReport[] };

function dateLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getMonth() + 1}/${at.getDate()}`;
}

export function AdminReportsPage() {
  const { toast } = useUi();
  const [filter, setFilter] = useState<Filter>(undefined);
  const [target, setTarget] = useState<ApiAdminReport | null>(null);
  const [list, setList] = useState<List>({ state: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let alive = true;
    getAdminReports(filter)
      .then((page) => alive && setList({ state: "ok", rows: page.items }))
      .catch((e: unknown) => {
        if (!alive) return;
        setList({
          state: "error",
          message: e instanceof ApiError ? e.message : "신고 목록을 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [filter, retry]);

  const onResolve = async (action: ReportActionType, label: string, note: string) => {
    if (!target) return;
    try {
      await resolveReport(target.report.id, {
        expectedVersion: target.report.version,
        // 명세는 publicReason을 필수로 본다. 메모를 비우면 조치 문구를 사유로 남긴다
        publicReason: note || label,
        action,
        internalNote: note || undefined,
      });
      toast(`${target.report.targetMember.displayName} 님 신고를 ${label} 처리했어요`);
      setRetry((n) => n + 1);
    } catch (e: unknown) {
      if (e instanceof ApiError && e.code === "VERSION_CONFLICT") {
        toast("다른 곳에서 먼저 처리됐어요. 목록을 새로 불러왔어요");
        setRetry((n) => n + 1);
        return;
      }
      toast(e instanceof ApiError ? e.message : "처리하지 못했어요");
    }
  };

  return (
    <div className={c("adm-body")}>
      <div className={c("adm-filter")}>
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            className={c(`adm-chip${filter === f.value ? " adm-chip--on" : ""}`)}
            onClick={() => {
              setList({ state: "loading" });
              setFilter(f.value);
            }}
          >
            {f.label}
          </button>
        ))}
      </div>

      {list.state === "loading" && (
        <Loading label="신고를 불러오는 중">
          <div className={c("adm-card adm-card--list")}>
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
        <div className={c("adm-card adm-card--list")}>
          {list.rows.length === 0 ? (
            <p className={c("t-caption adm-muted adm-empty")}>해당 상태의 신고가 없어요.</p>
          ) : (
            list.rows.map((row) => {
              const report = row.report;
              const done = report.status === "RESOLVED";
              return (
                <div key={report.id} className={c("adm-row")}>
                  <div className={c("adm-row-body")}>
                    <div className={c("adm-row-head")}>
                      <span className={c("t-body adm-row-name")}>
                        {report.targetMember.displayName} 님
                      </span>
                      <span className={c(`adm-pill ${done ? "adm-pill--done" : "adm-pill--pending"}`)}>
                        {done && report.action
                          ? `처리 완료 · ${ACTION_LABEL[report.action]}`
                          : done
                            ? "처리 완료"
                            : "확인 중"}
                      </span>
                    </div>
                    <div className={c("t-caption adm-muted adm-row-sub")}>
                      {REASON_LABEL[report.reasonCode]} · 신고자 {row.reporter.displayName} ·{" "}
                      {dateLabel(report.createdAt)}
                    </div>
                  </div>
                  {!done ? (
                    <Button variant="secondary" size="sm" onClick={() => setTarget(row)}>
                      처리
                    </Button>
                  ) : null}
                </div>
              );
            })
          )}
        </div>
      )}

      <p className={c("t-caption adm-muted")}>
        ‘처리’를 누르면 경고 · 일시정지 · 영구정지(탈퇴) · 반려 중에서 조치를 선택해요. 처리 결과는 신고자와 대상에게
        알림으로 전달돼요.
      </p>

      {target ? (
        <ActionModal
          name={target.report.targetMember.displayName}
          onClose={() => setTarget(null)}
          onConfirm={onResolve}
        />
      ) : null}
    </div>
  );
}
