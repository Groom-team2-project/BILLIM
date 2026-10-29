import { useEffect, useState } from "react";
import { ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ApiError } from "@/api/client";
import { type ApiMySanction, getMySanctions } from "@/api/reports";
import { PageHeader } from "@/components/layout/header";
import styles from "@/pages/profile/SanctionsPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/**
 * 조치 종류 문구. 처리 결과 알림과 같은 말을 쓴다.
 * 알림에서 "7일 이용 정지 조치가 적용됐어요"를 읽고 들어온 사람이 같은 말을 봐야 한다.
 */
const ACTION_LABEL: Record<ApiMySanction["action"], string> = {
  WARN: "경고",
  SUSPEND_7D: "7일 이용 정지",
  SUSPEND_30D: "30일 이용 정지",
  BAN: "영구 정지",
};

type List =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; rows: ApiMySanction[] };

function dateLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getFullYear()}년 ${at.getMonth() + 1}월 ${at.getDate()}일`;
}

/** 조치의 상태 한 줄. 경고는 기간이 없어 따로 쓸 말이 없다 */
function periodLabel(row: ApiMySanction): string | null {
  if (row.revokedAt) {
    return `${dateLabel(row.revokedAt)}에 해제됐어요`;
  }
  if (row.action === "BAN") {
    return "기간이 정해지지 않은 조치예요";
  }
  if (row.endsAt) {
    const until = `${dateLabel(row.endsAt)}까지`;
    return row.isEffective ? `${until} 이용이 제한돼요` : `${until}였어요`;
  }
  return null;
}

/**
 * 내가 받은 운영 조치 내역 (E_072).
 *
 * 처리 결과 알림이 "자세한 내용은 내 프로필에서 확인해 주세요"로 안내하는 그 화면이다.
 * 신고자·운영 메모·신고 대상 거래는 서버가 내려주지 않는다.
 * (도메인 가이드 결정 '조치 사유를 누구에게 보여줄지')
 */
export function SanctionsPage() {
  const [list, setList] = useState<List>({ state: "loading" });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let alive = true;
    getMySanctions()
      .then((page) => alive && setList({ state: "ok", rows: page.items }))
      .catch((e: unknown) => {
        if (!alive) return;
        setList({
          state: "error",
          message: e instanceof ApiError ? e.message : "운영 조치 내역을 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [retry]);

  return (
    <div className={c("sanctions page-window")}>
      <PageHeader title="받은 운영 조치" backTo="/profile" />
      <p className={c("t-body sanctions-muted")}>
        운영팀이 내 계정에 적용한 조치예요. 조치에 이의가 있으면 고객센터로 알려 주세요.
      </p>

      {list.state === "loading" && (
        <Loading label="운영 조치 내역을 불러오는 중">
          <div className={c("sanctions-card")}>
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
        <div className={c("sanctions-card")}>
          {list.rows.length === 0 ? (
            <p className={c("t-caption sanctions-muted sanctions-empty")}>받은 조치가 없어요.</p>
          ) : (
            list.rows.map((row) => {
              const period = periodLabel(row);
              return (
                <div key={row.id} className={c("sanctions-row")}>
                  <div className={c("sanctions-row-head")}>
                    <span className={c("t-body sanctions-row-name")}>{ACTION_LABEL[row.action]}</span>
                    {/* 경고는 이용을 제한하지 않는다. '종료' 배지를 붙이면 끝난 제재처럼 읽힌다 */}
                    {row.action === "WARN" ? null : (
                      <span
                        className={c(
                          `sanctions-pill ${row.isEffective ? "sanctions-pill--on" : "sanctions-pill--off"}`,
                        )}
                      >
                        {row.isEffective ? "적용 중" : "종료"}
                      </span>
                    )}
                  </div>
                  {/* 운영자가 쓴 최소 사유. 이게 안 보여서 대상이 이유를 몰랐다 */}
                  <div className={c("t-caption sanctions-muted sanctions-reason")}>{row.publicReason}</div>
                  <div className={c("t-caption sanctions-muted")}>{dateLabel(row.decidedAt)}</div>
                  {period ? <div className={c("t-caption sanctions-muted")}>{period}</div> : null}
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
