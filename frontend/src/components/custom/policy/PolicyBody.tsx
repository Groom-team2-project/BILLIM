import type { ReactNode } from "react";
import styles from "@/components/custom/policy/PolicyBody.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

function inline(text: string): ReactNode[] {
  return text.split("**").map((s, i) => (i % 2 === 1 ? <strong key={i}>{s}</strong> : s));
}

/** 정책 본문 렌더러 — 문단·굵게·목록·표 (프로토타입 mdBody 이식) */
export function PolicyBody({ md }: { md: string }) {
  const lines = md.split("\n");
  const out: ReactNode[] = [];
  let i = 0;
  let k = 0;
  const cells = (row: string) =>
    row.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());

  while (i < lines.length) {
    const line = lines[i].trim();
    if (line === "") {
      i++;
      continue;
    }
    if (line.startsWith("|")) {
      const rows: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith("|")) {
        rows.push(lines[i]);
        i++;
      }
      const head = cells(rows[0]);
      const body = rows.slice(2).map(cells);
      out.push(
        <div key={k++} className={c("pmd-scroll")}>
          <table className={c("pmd-table")}>
            <thead>
              <tr>{head.map((h, ci) => <th key={ci}>{inline(h)}</th>)}</tr>
            </thead>
            <tbody>
              {body.map((r, ri) => (
                <tr key={ri}>{r.map((c, ci) => <td key={ci}>{inline(c)}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }
    if (line.startsWith("- ")) {
      const items: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith("- ")) {
        items.push(lines[i].trim().slice(2));
        i++;
      }
      out.push(<ul key={k++} className={c("pmd-ul")}>{items.map((t, ii) => <li key={ii}>{inline(t)}</li>)}</ul>);
      continue;
    }
    if (/^\d+\. /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\. /.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+\. /, ""));
        i++;
      }
      out.push(<ol key={k++} className={c("pmd-ol")}>{items.map((t, ii) => <li key={ii}>{inline(t)}</li>)}</ol>);
      continue;
    }
    out.push(<p key={k++} className={c("pmd-p")}>{inline(line)}</p>);
    i++;
  }

  return <div className={c("pmd")}>{out}</div>;
}
