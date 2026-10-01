import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { PageHeader } from "@/components/layout/header";
import { Alert } from "@/components/ui/alert";
import { NEARBY_NEIGHBORHOODS } from "@/data/community";
import { addCommunity } from "@/stores/community";
import { MapView } from "@/components/custom/items/MapView";
import styles from "@/pages/onboarding/VerifyPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 동네 인증 — 가입 흐름(기본)과 동네 추가 흐름(flow=add) 공용 */
export function VerifyPage() {
  const navigate = useNavigate();
  const { toast } = useUi();
  const [params] = useSearchParams();
  const name = params.get("name") ?? "새솔마을 5단지";
  const isAdd = params.get("flow") === "add";
  const backTo = isAdd ? "/neighborhoods" : "/join";

  const onVerify = () => {
    if (isAdd) {
      const region = NEARBY_NEIGHBORHOODS.find((n) => n.name === name)?.region ?? "대전 유성구";
      addCommunity(name, region);
      toast(`${name} 동네 인증을 마쳤어요`);
      navigate("/");
    } else {
      navigate("/joindone");
    }
  };

  return (
    <main className={c("verify")}>
      <PageHeader title="동네 인증" backTo={backTo} standalone />
      <div className={c("verify-body")}>
        <MapView selected="a" height={200} />

        <div>
          <h1 className={c("t-title-lg verify-title")}>{name}에 계신가요?</h1>
          <p className={c("t-body verify-muted verify-desc")}>
            지금 이 단지 안에 있는지 위치로 한 번만 확인해요. "이 동네에 사는 이웃"인지 확인하는 절차예요.
          </p>
        </div>

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

        <Alert tone="info" title="위치 권한이 필요해요">
          인증할 때 한 번만 위치를 확인하고, 이후에는 사용하지 않아요.
        </Alert>
      </div>

      <div className={c("verify-actions")}>
        <Button size="lg" block icon="pin" onClick={onVerify}>
          현재 위치로 동네 인증하기
        </Button>
        <Link to={backTo} className={c("verify-other t-label")}>다른 동네 선택</Link>
      </div>
    </main>
  );
}
