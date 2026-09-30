import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { BarsCat, BarsWeek, StatTile } from "@/components/custom/admin/charts";
import { COMM, COMM_ORDER } from "@/data/admin";
import { useReports } from "@/stores/admin";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

export function AdminDashboardPage() {
  const navigate = useNavigate();
  const [comm, setComm] = useState(COMM_ORDER[0]);
  const reports = useReports();
  const d = COMM[comm];
  const pending = reports.filter((r) => r.status === "pending" && r.community === comm).length;
  const pendingAll = reports.filter((r) => r.status === "pending").length;

  return (
    <div className={c("adm-body")}>
      <div>
        <p className={c("t-label adm-muted adm-section-label")}>동네 선택</p>
        <div className={c("adm-comm")}>
          {COMM_ORDER.map((name) => (
            <button
              key={name}
              type="button"
              className={c(`adm-comm-b${comm === name ? " adm-comm-b--on" : ""}`)}
              onClick={() => setComm(name)}
            >
              {name}
            </button>
          ))}
        </div>
      </div>

      <div className={c("adm-kpis")}>
        <StatTile label="등록 물건" value={String(d.items)} sub="전체 누적" />
        <StatTile label="활성 대여" value={String(d.active)} sub="진행 중" />
        <StatTile label="이번 주 신규" value={`${d.newItems}개`} sub="물건" />
        <StatTile label="신규 이웃" value={String(d.newUsers)} sub="최근 7일" />
        <StatTile label="대여 성사율" value={`${d.rate}%`} sub="요청 대비" />
        <StatTile
          label="미처리 신고"
          value={String(pending)}
          sub={pending > 0 ? "확인 필요" : "모두 처리됨"}
          alert={pending > 0}
        />
      </div>

      <BarsWeek title={`주간 대여 건수 · ${comm}`} data={d.week} />
      <BarsCat title="카테고리별 등록 물건 (10개 분류)" data={d.cats} />

      <div className={c("adm-card")}>
        <p className={c("t-label adm-chart-title")}>인기 물건 TOP 3</p>
        <div className={c("adm-pop-list")}>
          {d.popular.map(([title, count], i) => (
            <div key={title} className={c("adm-pop-row")}>
              <span className={c("adm-rank")}>{i + 1}</span>
              <span className={c("adm-pop-title")}>{title}</span>
              <span className={c("t-caption adm-muted")}>{count}회 대여</span>
            </div>
          ))}
        </div>
      </div>

      <Button variant="secondary" block onClick={() => navigate("/admin/reports")}>
        신고 관리로 이동 (미처리 {pendingAll}건)
      </Button>
      <p className={c("t-caption adm-muted")}>
        실서비스에선 Grafana 같은 도구와 연동해 실시간 지표를 봐요. 여기 수치는 동네별 예시예요.
      </p>
    </div>
  );
}
