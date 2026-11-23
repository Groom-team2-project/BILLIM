import { Link, useParams } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { Alert } from "@/components/ui/alert";
import { HUBS, POLICIES, policyTitle } from "@/data/policy";
import { PolicyBody } from "@/components/custom/policy/PolicyBody";
import styles from "@/pages/policy/PolicyPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 정책·안내 화면 — 본문(정책 6종) · 허브(소개·고객센터) · 작성 전 자리 표시 */
export function PolicyPage() {
  const { key: raw } = useParams();
  const key = raw ?? "help";
  const hub = HUBS[key];
  const policy = POLICIES[key];

  return (
    <div className={c("policy page-window")}>
      <PageHeader title={policyTitle(key)} />

      {hub ? (
        <>
          <p className={c("t-body policy-muted")}>{hub.body}</p>
          <div className={c("policy-card")}>
            {hub.links.map(([to, label]) => (
              <Link key={to} to={`/policy/${to}`} className={c("policy-row")}>
                <span className={c("t-body policy-row-title")}>{label}</span>
                <Icon name="chevronR" size={18} />
              </Link>
            ))}
          </div>
        </>
      ) : policy ? (
        <PolicyBody md={policy.md} />
      ) : (
        <Alert tone="info" title="작성 예정 페이지예요">
          핵심 정책·안전 안내(커뮤니티 규칙, 안전 거래 가이드, 금지 품목, 분실·파손, 분쟁·신고, 신뢰 지표)부터 먼저
          작성했어요. 이 항목은 이후에 채워 넣으면 돼요.
        </Alert>
      )}
    </div>
  );
}
