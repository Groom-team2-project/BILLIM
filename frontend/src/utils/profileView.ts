import type { ApiTrustMetrics } from "@/api/members";
import type { Trust } from "@/types/item";
import { formatYearMonth } from "@/utils/itemView";

/** 소속·가입 시점·상태 요약 한 줄 — 두 프로필 화면 공통 형식 */
export function describeMembership(opts: {
  communityName?: string;
  joinedAt: string;
  verified: boolean;
  restricted?: boolean;
  /** 미인증 문구 — 내 프로필은 재인증 유도 */
  unverifiedText?: string;
}): string {
  const parts = [
    opts.communityName,
    `${formatYearMonth(opts.joinedAt)} 가입`,
    opts.verified ? "동네 인증됨" : (opts.unverifiedText ?? "동네 미인증"),
  ];
  if (opts.restricted) parts.push("이용 제한 중");
  return parts.filter(Boolean).join(" · ");
}

/** TrustMetrics → TrustLines 입력. 지표 조회 실패 시 가입 시점만 */
export function toTrust(metrics: ApiTrustMetrics | null, joinedAt: string): Trust {
  const since = formatYearMonth(joinedAt);
  if (metrics === null) return { kept: 0, total: 0, done: 0, since };
  return {
    kept: metrics.returnPromiseKept,
    total: metrics.returnPromiseTotal,
    done: metrics.completedCount,
    lent: metrics.lentCount,
    since,
  };
}
