import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useUi } from "@/components/overlay/UiContext";
import { ActionModal } from "@/components/custom/admin/ActionModal";
import type { Report } from "@/data/admin";
import { resolveReport, useReports } from "@/stores/admin";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

const FILTERS = [
  { value: "all", label: "전체" },
  { value: "pending", label: "확인 중" },
  { value: "done", label: "처리 완료" },
] as const;

type Filter = (typeof FILTERS)[number]["value"];

export function AdminReportsPage() {
  const { toast } = useUi();
  const [filter, setFilter] = useState<Filter>("all");
  const [target, setTarget] = useState<Report | null>(null);
  const reports = useReports();
  const rows = reports.filter((r) => (filter === "all" ? true : r.status === filter));

  return (
    <div className={c("adm-body")}>
      <div className={c("adm-filter")}>
        {FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={c(`adm-chip${filter === f.value ? " adm-chip--on" : ""}`)}
            onClick={() => setFilter(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      <div className={c("adm-card adm-card--list")}>
        {rows.length === 0 ? (
          <p className={c("t-caption adm-muted adm-empty")}>해당 상태의 신고가 없어요.</p>
        ) : (
          rows.map((r) => (
            <div key={r.id} className={c("adm-row")}>
              <div className={c("adm-row-body")}>
                <div className={c("adm-row-head")}>
                  <span className={c("t-body adm-row-name")}>{r.name} 님</span>
                  <span className={c(`adm-pill ${r.status === "done" ? "adm-pill--done" : "adm-pill--pending"}`)}>
                    {r.status === "done" ? `처리 완료 · ${r.action}` : "확인 중"}
                  </span>
                </div>
                <div className={c("t-caption adm-muted adm-row-sub")}>
                  {r.reason} · {r.community} · {r.date}
                </div>
              </div>
              {r.status === "pending" ? (
                <Button variant="secondary" size="sm" onClick={() => setTarget(r)}>처리</Button>
              ) : null}
            </div>
          ))
        )}
      </div>

      <p className={c("t-caption adm-muted")}>
        ‘처리’를 누르면 경고 · 일시정지 · 영구정지(탈퇴) · 반려 중에서 조치를 선택해요. 처리 결과는 신고자와 대상에게
        알림으로 전달돼요.
      </p>

      {target ? (
        <ActionModal
          name={target.name}
          onClose={() => setTarget(null)}
          onConfirm={(action) => {
            resolveReport(target.id, action);
            toast(`${target.name} 님 신고를 ${action} 처리했어요`);
          }}
        />
      ) : null}
    </div>
  );
}
