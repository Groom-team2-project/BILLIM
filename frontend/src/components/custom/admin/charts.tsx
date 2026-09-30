import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);
/** 대시보드 지표 타일·차트 — 프로토타입 SVG 도식. 실서비스는 관측 도구 연동 */

export function StatTile({ label, value, sub, alert }: { label: string; value: string; sub: string; alert?: boolean }) {
  return (
    <div className={c("adm-card adm-kpi")}>
      <div className={c("t-caption adm-muted")}>{label}</div>
      <div className={c("adm-kpi-n")} style={alert ? { color: "var(--status-overdue-fg)" } : undefined}>{value}</div>
      <div className={c("t-caption adm-muted")}>{sub}</div>
    </div>
  );
}

export function BarsWeek({ title, data }: { title: string; data: [string, number][] }) {
  const max = Math.max(...data.map((d) => d[1])) || 1;
  const W = 320;
  const H = 150;
  const base = H - 22;
  const top = 16;
  const bw = W / data.length;
  return (
    <div className={c("adm-card")}>
      <p className={c("t-label adm-chart-title")}>{title}</p>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={title} style={{ display: "block" }}>
        <line x1={0} y1={base} x2={W} y2={base} stroke="var(--line)" strokeWidth={1} />
        {data.map((d, i) => {
          const bh = Math.round((d[1] / max) * (base - top));
          const w = bw - 14;
          const x = i * bw + 7;
          const y = base - bh;
          return (
            <g key={d[0]}>
              <title>{`${d[0]}요일 ${d[1]}건`}</title>
              <rect x={x} y={y} width={w} height={bh} rx={4} fill="var(--action)" />
              <text x={x + w / 2} y={y - 4} textAnchor="middle" fontSize={10} fontWeight={700} fill="var(--ink)">{d[1]}</text>
              <text x={x + w / 2} y={H - 6} textAnchor="middle" fontSize={10} fill="var(--ink-muted)">{d[0]}</text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export function BarsCat({ title, data }: { title: string; data: [string, number][] }) {
  const max = Math.max(...data.map((d) => d[1])) || 1;
  return (
    <div className={c("adm-card")}>
      <p className={c("t-label adm-chart-title")}>{title}</p>
      <div className={c("adm-cat-list")}>
        {data.map((d) => {
          const pct = Math.round((d[1] / max) * 100);
          return (
            <div key={d[0]} className={c("adm-cat-row")}>
              <span className={c("adm-cat-label")}>{d[0]}</span>
              <div className={c("adm-cat-track")}>
                <div className={c("adm-cat-fill")} style={{ width: `${pct}%` }} />
              </div>
              <span className={c("adm-cat-n")}>{d[1]}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
