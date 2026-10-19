import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getTrustMetrics, type ApiTrustMetrics } from "@/api/members";
import { ApiError } from "@/api/client";
import { ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { TrustLines } from "@/components/custom/profile/OwnerCard";
import { toTrust } from "@/utils/rentalOverview";

type Props = { memberId: string; since?: string };

/** 신뢰 지표는 실패 시 0이나 샘플 기록으로 대체하지 않음 */
export function LiveTrustLines({ memberId, since }: Props) {
  return <TrustRequest key={memberId} memberId={memberId} since={since} />;
}

function TrustRequest({ memberId, since }: Props) {
  const navigate = useNavigate();
  const [metrics, setMetrics] = useState<ApiTrustMetrics | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let alive = true;
    getTrustMetrics(memberId).then((value) => { if (alive) setMetrics(value); }).catch((e: unknown) => {
      if (!alive) return;
      if (e instanceof ApiError && e.unauthenticated) { navigate("/login"); return; }
      setError(e instanceof ApiError ? e.message : "거래 기록을 불러오지 못했어요.");
    });
    return () => { alive = false; };
  }, [memberId, retry, navigate]);
  if (error) return <ErrorState title="거래 기록을 불러오지 못했어요" onRetry={() => { setError(""); setRetry((n) => n + 1); }}>{error}</ErrorState>;
  if (!metrics) return <Loading label="거래 기록 불러오는 중"><Skeleton /><Skeleton /></Loading>;
  return <><TrustLines trust={toTrust(metrics, since)} /><p className="t-caption">{metrics.basis}</p></>;
}
