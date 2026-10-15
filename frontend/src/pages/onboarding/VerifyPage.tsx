import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { PageHeader } from "@/components/layout/header";
import { Alert } from "@/components/ui/alert";
import { ErrorState } from "@/components/ui/state";
import { addCommunity } from "@/stores/community";
import { MapView } from "@/components/custom/items/MapView";
import { ApiError } from "@/api/client";
import { type ApiCommunity, getCommunity, verifyLocation } from "@/api/communities";
import { GeoError, getCurrentCoords } from "@/utils/geolocation";
import styles from "@/pages/onboarding/VerifyPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 서버 코드별 안내. 반경 밖·정확도 초과는 사용자가 할 수 있는 행동이 다르다 (에러 코드 — community) */
function verifyMessage(e: ApiError): string {
  if (e.code === "LOCATION_OUTSIDE") {
    return "선택한 동네 반경 밖이에요. 그 동네 안에서 다시 시도하거나 다른 동네를 선택해 주세요.";
  }
  if (e.code === "LOCATION_INACCURATE") {
    return "위치 정확도가 낮아 확인할 수 없어요. 실외에서 잠시 후 다시 시도해 주세요.";
  }
  return e.message;
}

/** 동네 인증 — 가입 흐름(기본)과 동네 추가 흐름(flow=add) 공용 */
export function VerifyPage() {
  const navigate = useNavigate();
  const { toast } = useUi();
  const [params] = useSearchParams();
  const communityId = params.get("communityId");
  const isAdd = params.get("flow") === "add";
  const backTo = isAdd ? "/neighborhoods" : "/join";

  const [community, setCommunity] = useState<ApiCommunity | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  // 동네를 안 고르고 들어온 경우는 렌더 중에 알 수 있다. 효과로 상태를 세우지 않는다
  const error = communityId ? loadError : "동네를 먼저 선택해 주세요.";

  useEffect(() => {
    if (!communityId) return;
    let alive = true;
    getCommunity(communityId)
      .then((found) => alive && setCommunity(found))
      .catch((e: unknown) => {
        if (!alive) return;
        setLoadError(e instanceof ApiError ? e.message : "동네 정보를 불러오지 못했어요.");
      });
    return () => {
      alive = false;
    };
  }, [communityId, retry]);

  const onVerify = async () => {
    if (!communityId) return;
    setVerifying(true);
    setVerifyError(null);
    try {
      const coords = await getCurrentCoords();
      const membership = await verifyLocation(communityId, coords);

      if (isAdd) {
        addCommunity(membership.communityName, community?.displayAddress ?? "");
        toast(`${membership.communityName} 동네 인증을 마쳤어요`);
        navigate("/");
      } else {
        navigate(`/joindone?communityId=${membership.communityId}`);
      }
    } catch (e: unknown) {
      if (e instanceof GeoError) setVerifyError(e.message);
      else if (e instanceof ApiError) setVerifyError(verifyMessage(e));
      else setVerifyError("인증하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setVerifying(false);
    }
  };

  const name = community?.name ?? "";

  return (
    <main className={c("verify")}>
      <PageHeader title="동네 인증" backTo={backTo} standalone />
      <div className={c("verify-body")}>
        <MapView selected="a" height={200} />

        {error ? (
          <ErrorState title="동네를 확인할 수 없어요" onRetry={
              communityId
                ? () => {
                    setLoadError(null);
                    setRetry((n) => n + 1);
                  }
                : undefined
            }>
            {error}
          </ErrorState>
        ) : (
          <div>
            <h1 className={c("t-title-lg verify-title")}>{name ? `${name}에 계신가요?` : "동네를 확인하는 중…"}</h1>
            <p className={c("t-body verify-muted verify-desc")}>
              지금 이 단지 안에 있는지 위치로 한 번만 확인해요. "이 동네에 사는 이웃"인지 확인하는 절차예요.
            </p>
          </div>
        )}

        <div className={c("verify-card")}>
          <div className={c("verify-row t-label")}>
            <Icon name="shield" size={18} />
            이렇게 보호해요
          </div>
          <div className={c("verify-row verify-muted t-caption")}>
            <Icon name="check" size={16} />
            단지 반경 안에 있는지 여부만 확인해요
          </div>
          <div className={c("verify-row verify-muted t-caption")}>
            <Icon name="check" size={16} />
            정확한 좌표·집 주소는 저장하지 않아요
          </div>
          <div className={c("verify-row verify-muted t-caption")}>
            <Icon name="check" size={16} />
            반경 밖이면 인증되지 않아요
          </div>
        </div>

        {verifyError ? (
          <Alert tone="warning" title="인증되지 않았어요">
            {verifyError}
          </Alert>
        ) : (
          <Alert tone="info" title="위치 권한이 필요해요">
            인증할 때 한 번만 위치를 확인하고, 이후에는 사용하지 않아요.
          </Alert>
        )}
      </div>

      <div className={c("verify-actions")}>
        <Button size="lg" block icon="pin" onClick={onVerify} disabled={verifying || !community}>
          {verifying ? "위치 확인 중…" : "현재 위치로 동네 인증하기"}
        </Button>
        <Link to={backTo} className={c("verify-other t-label")}>다른 동네 선택</Link>
      </div>
    </main>
  );
}
