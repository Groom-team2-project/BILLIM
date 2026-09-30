import { useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Icon } from "@/components/ui/icon";
import { PlaceCard } from "@/components/ui/place-card";
import { DateRange } from "@/components/ui/date-range";
import { Meta } from "@/components/ui/meta";
import { SectionHead } from "@/components/ui/section-head";
import { useUi } from "@/components/overlay/UiContext";
import { md } from "@/utils/date";
import { PhotoSlider } from "@/components/custom/items/PhotoSlider";
import { CATEGORY_MAP, MOCK_ITEMS } from "@/data/items";
import { OwnerCard } from "@/components/custom/profile/OwnerCard";
import styles from "@/pages/items/ItemDetailPage.module.css";
import { classes } from "@/utils/classes";
import { MOCK_TODAY } from "@/constants/mock";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { useScreenState } from "@/hooks/useScreenState";
import { Alert } from "@/components/ui/alert";
import { setVisibility, useMyItems, useVisibility } from "@/stores/items";
import { objectParticle } from "@/utils/korean";

const c = classes(styles);

const AVAIL_FROM = new Date(2026, 8, 1);
const AVAIL_TO = new Date(2026, 9, 31);
const BOOKED: DayRange[] = [[new Date(2026, 8, 23), new Date(2026, 8, 24)]];

export function ItemDetailPage() {
  const { confirm, report, toast } = useUi();
  const navigate = useNavigate();
  const { itemId } = useParams();
  const [range, setRange] = useState<DayRange>([new Date(2026, 8, 26), new Date(2026, 8, 27)]);
  const state = useScreenState();
  const [params] = useSearchParams();
  /* 프로토타입 상세 전용 상태: 날짜 충돌(unavailable) · 내 물건(own) */
  const variant = params.get("state");
  const unavailable = variant === "unavailable";
  const myItems = useMyItems();
  const item = MOCK_ITEMS.find((i) => String(i.id) === itemId) ?? MOCK_ITEMS[0];
  const category = CATEGORY_MAP[item.cat];
  const own = variant === "own" || myItems.some((m) => m.id === item.id);
  const myItem = myItems.find((m) => m.id === item.id);
  const visibility = useVisibility(item.id);

  const onRequest = () => navigate(`/items/${item.id}/request`);

  if (state === "loading") {
    return (
      <Loading label="물건 정보 불러오는 중">
        <div className={c("detail-loading")}>
          <Skeleton h={280} r={16} />
          <Skeleton w="70%" h={22} />
          <Skeleton w="45%" />
          <Skeleton h={96} r={12} />
          <Skeleton h={260} r={12} />
        </div>
      </Loading>
    );
  }
  if (state === "error") {
    return (
      <ErrorState title="이 물건을 볼 수 없어요">
        삭제되었거나 비공개로 바뀐 물건일 수 있어요. 다른 물건을 찾아보세요.
      </ErrorState>
    );
  }
  if (state === "empty") {
    return (
      <EmptyState icon="grid" title="이 물건은 더 이상 없어요">
        소유자가 등록을 내렸어요. 비슷한 물건을 찾아볼 수 있어요.
      </EmptyState>
    );
  }

  return (
    <div className={c("detail")}>
      <div className={c("detail-two")}>
        <div className={c("detail-main")}>
          <PhotoSlider cat={item.cat} count={item.photos} title={item.title} className={c("detail-photo")} />

          <div className={c("detail-head")}>
            <div className={c("detail-tags t-micro")}>
              <span>{category.label}</span>
              <span>· {item.ago} 등록</span>
            </div>
            <h1 className={c("t-title-lg detail-title")}>{item.title}</h1>
            <Meta icon="check" strong>9/26(토) – 9/27(일) 대여 가능</Meta>
          </div>

          <p className={c("t-body detail-desc")}>
            배터리 2개와 비트 12종 세트를 같이 빌려드려요. 선반 달기, 가구 조립 정도에 충분해요.
            쓰고 나서 비트는 케이스에 다시 넣어 주세요.
          </p>

          <PlaceCard place={item.place} detail="택배함 왼쪽 벤치에서 주고받아요" distance={item.dist} />

          {unavailable ? (
            <Alert tone="warning" title="선택한 날짜에 이미 승인된 대여가 있어요">
              9/23(수) – 9/24(목)은 다른 이웃이 빌리기로 했어요. 9/25(금)부터 가능해요.
            </Alert>
          ) : null}

          <section className={c("detail-cal")} aria-labelledby="cal-h">
            <SectionHead id="cal-h" title="대여 가능한 날짜" />
            <span className={c("t-caption detail-muted detail-cal-range")}>9/1 – 10/31</span>
            <Calendar today={MOCK_TODAY} from={AVAIL_FROM} to={AVAIL_TO} booked={BOOKED} value={range} onChange={setRange} />
            <p className={c("t-caption detail-muted detail-cal-note")}>
              시작일과 반납일 모두 대여 기간에 포함돼요. 표시는 지금 기준이고, 승인할 때 다시 확인해요.
            </p>
          </section>
        </div>

        <div className={c("detail-aside")}>
          <div className={c("only-desk")}>
            <div className={c("detail-request")}>
              {own ? (
                <>
                  <span className={c("t-label")}>내가 올린 물건이에요</span>
                  <Meta icon={visibility === "PUBLIC" ? "check" : "lock"} strong>
                    {visibility === "PUBLIC" ? "공개 중 · 이웃이 찾을 수 있어요" : "공개 중지 · 검색과 새 요청이 멈춰 있어요"}
                  </Meta>
                  <Button block onClick={() => navigate("/rentals")}>요청 보기 (1)</Button>
                  <Button variant="secondary" block onClick={() => navigate("/register")}>수정</Button>
                  <Button
                    variant="ghost"
                    block
                    icon={visibility === "PUBLIC" ? "lock" : "check"}
                    onClick={() => {
                      if (visibility === "PUBLIC") {
                        confirm({
                          title: `${item.title}${objectParticle(item.title)} 공개 중지할까요?`,
                          body: "검색과 새 요청이 멈춰요. 진행 중인 대여는 그대로 유지돼요.",
                          confirm: "공개 중지",
                          onConfirm: () => {
                            setVisibility(item.id, "HIDDEN");
                            toast("공개를 중지했어요");
                          },
                        });
                      } else {
                        setVisibility(item.id, "PUBLIC");
                        toast("다시 공개했어요");
                      }
                    }}
                  >
                    {visibility === "PUBLIC" ? "공개 중지" : "다시 공개"}
                  </Button>
                </>
              ) : (
                <>
                  <span className={c("t-micro detail-muted")}>선택한 기간</span>
                  {unavailable ? (
                    <span className={c("t-label detail-conflict")}>9/23 – 9/24는 이미 예약됐어요</span>
                  ) : (
                    <DateRange start={md(range[0])} end={md(range[1])} />
                  )}
                  <Meta icon="pin">{item.place}</Meta>
                  <Button size="lg" block disabled={unavailable} onClick={onRequest}>대여 요청</Button>
                  <Button variant="secondary" block icon="chat" onClick={() => navigate("/chat/c1")}>
                    {item.owner} 님에게 채팅으로 물어보기
                  </Button>
                  <span className={c("t-caption detail-muted")}>
                    요청은 무료예요. {item.owner} 님이 승인하면 알려드려요.
                  </span>
                </>
              )}
            </div>
          </div>
          <OwnerCard owner={item.owner} trust={item.trust} />
          {own ? null : (
          <button
            type="button"
            className={c("t-label detail-report")}
            onClick={() => report({ name: item.owner, onConfirm: () => toast("신고를 접수했어요") })}
          >
            {item.owner} 님 신고하기
          </button>
          )}
        </div>
      </div>

      <div className={c("detail-actionbar only-mobile")}>
        {own ? (
          <>
            <div className={c("detail-actionbar-info")}>
              <span className={c("t-label")}>내가 올린 물건이에요</span>
              <span className={c("t-caption detail-muted")}>
                {myItem && myItem.requests > 0 ? `대기 중인 요청 ${myItem.requests}건` : "대기 중인 요청 없음"}
              </span>
            </div>
            <Button variant="secondary" onClick={() => navigate("/register")}>수정</Button>
            <Button onClick={() => navigate("/rentals")}>요청 보기</Button>
          </>
        ) : (
          <>
            <div className={c("detail-actionbar-info")}>
              <DateRange start={md(range[0])} end={md(range[1])} icon={false} len={false} className={c("detail-actionbar-range")} />
              <span className={c("t-caption detail-muted")}>무료 · {item.place}</span>
            </div>
            <button
              type="button"
              className={c("detail-chat-button")}
              aria-label={`${item.owner} 님과 채팅`}
              onClick={() => navigate("/chat/c1")}
            >
              <Icon name="chat" size={22} />
            </button>
            <Button size="lg" disabled={unavailable} onClick={onRequest}>대여 요청</Button>
          </>
        )}
      </div>
    </div>
  );
}
