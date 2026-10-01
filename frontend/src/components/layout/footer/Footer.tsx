import { Link } from "react-router-dom";
import styles from "@/components/layout/footer/Footer.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 프로토타입 Footer — 링크는 정책·안내 화면으로 연결 */
const GROUPS: { title: string; links: [string, string][] }[] = [
  {
    title: "서비스",
    links: [
      ["서비스 소개", "/policy/about"],
      ["커뮤니티 규칙", "/policy/community"],
    ],
  },
  {
    title: "정책·지원",
    links: [
      ["이용약관", "/policy/terms"],
      ["개인정보처리방침", "/policy/privacy"],
      ["고객센터", "/policy/help"],
    ],
  },
];

export function Footer() {
  return (
    <footer className={c("ft")}>
      <div className={c("ft-cols only-desk")}>
        {GROUPS.map((g) => (
          <div key={g.title} className={c("ft-col")}>
            <h4>{g.title}</h4>
            {g.links.map(([label, to]) => (
              <Link key={label} to={to}>{label}</Link>
            ))}
          </div>
        ))}
      </div>
      <div className={c("only-mobile")}>
        {GROUPS.map((g) => (
          <details key={g.title} className={c("ft-acc")}>
            <summary>{g.title}</summary>
            <div className={c("ft-acc-body")}>
              {g.links.map(([label, to]) => (
                <Link key={label} to={to}>{label}</Link>
              ))}
            </div>
          </details>
        ))}
      </div>
      <div className={c("ft-bottom")}>
        <span className={c("ft-logo")} role="img" aria-label="BILLIM">BILLIM</span>
        <p className={c("ft-safe")}>BILLIM의 대여는 모두 무료예요. 돈이나 보증금을 요구받으면 신고해 주세요.</p>
        <p className={c("ft-copy")}>Copyright © goorm team2</p>
      </div>
    </footer>
  );
}
