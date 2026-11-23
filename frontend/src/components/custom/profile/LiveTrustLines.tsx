import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMemberTrust, type ApiTrustMetrics } from "@/api/members";
import { ApiError } from "@/api/client";
import { ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { TrustLines } from "@/components/custom/profile/OwnerCard";
import { toTrust } from "@/utils/profileView";

type Props = { memberId: string; joinedAt?: string };

/** 지표 조회 실패는 거래 없음과 구분하고 해당 영역에서만 재시도 */
export function LiveTrustLines(props: Props) {
  return <TrustRequest key={props.memberId} {...props} />;
}

function TrustRequest({ memberId, joinedAt = "" }: Props) {
  const navigate = useNavigate();
  const [metrics, setMetrics] = useState<ApiTrustMetrics | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let alive = true;
    getMemberTrust(memberId).then((data) => { if (alive) setMetrics(data); }).catch((e: unknown) => {
      if (!alive) return;
      if (e instanceof ApiError && e.unauthenticated) { navigate("/login"); return; }
      setError(e instanceof ApiError ? e.message : "거래 기록을 불러오지 못했어요.");
    });
    return () => { alive = false; };
  }, [memberId, retry, navigate]);
  if (error) return <ErrorState title="거래 기록을 불러오지 못했어요" onRetry={() => { setError(""); setRetry((n) => n + 1); }}>{error}</ErrorState>;
  if (!metrics) return <Loading label="거래 기록 불러오는 중"><Skeleton w="70%" /><Skeleton w="55%" /></Loading>;
  return <TrustLines trust={toTrust(metrics, joinedAt)} />;
}
