import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, resetCsrf } from "@/api/client";
import { getMyProfile } from "@/api/members";
import { Button } from "@/components/ui/button";
import { signIn } from "@/stores/session";

/** 카카오 로그인 콜백 착지 지점 */
export function AuthCompletePage() {
  const navigate = useNavigate();
  const [failed, setFailed] = useState(false);

  const load = useCallback(() => {
    let canceled = false;

    getMyProfile()
      .then((profile) => {
        if (canceled) return;
        signIn(profile.role);
        // 활성 동네가 없으면 동네 찾기부터. 판정은 서버의 onboardingRequired
        navigate(profile.onboardingRequired ? "/join" : "/", { replace: true });
      })
      .catch((e) => {
        if (canceled) return;
        // 세션 없음(401)만 로그인 실패로 확정. 일시 장애는 세션을 둔 채 재시도 유도
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login?error=PROFILE_LOAD_FAILED", { replace: true });
        } else {
          setFailed(true);
        }
      });

    return () => {
      canceled = true;
    };
  }, [navigate]);

  useEffect(() => {
    // 로그인으로 세션 변경 — 이전 세션의 CSRF 토큰 무효
    resetCsrf();
    return load();
  }, [load]);

  if (failed) {
    return (
      <div>
        <p className="t-body">프로필을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        <Button
          variant="secondary"
          onClick={() => {
            setFailed(false);
            load();
          }}
        >
          다시 시도
        </Button>
      </div>
    );
  }

  return <p className="t-body">로그인 중…</p>;
}
