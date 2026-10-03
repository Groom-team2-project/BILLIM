import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
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
import { OwnerCard } from "@/components/custom/profile/OwnerCard";
import { ApiError } from "@/api/client";
import { type ApiItemDetail, changeItemVisibility, getItem } from "@/api/items";
import styles from "@/pages/items/ItemDetailPage.module.css";
import { classes } from "@/utils/classes";
import { EmptyState, ErrorState, Loading, Skeleton } from "@/components/ui/state";
import {
  UNKNOWN_OWNER,
  UNKNOWN_PLACE,
  formatAgo,
  formatDistance,
  formatMonthDay,
  formatYearMonth,
  parseIsoDate,
  toCategoryId,
} from "@/utils/itemView";
import { objectParticle } from "@/utils/korean";
import { setCurrentItem, useCurrentItem } from "@/stores/currentItem";

const c = classes(styles);

type Loaded =
  | { key: string; state: "ok" }
  | { key: string; state: "error"; status: number; message: string };

const dayN = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

/** 처음 보여 줄 선택 기간: 오늘과 대여 가능 시작일 중 늦은 날 하루. 가능 기간이 지났으면 없음 */
function defaultRange(from: Date, to: Date, today: Date): DayRange | null {
  const start = dayN(from) > dayN(today) ? from : today;
  return dayN(start) > dayN(to) ? null : [start, start];
}

export function ItemDetailPage() {
  const { confirm, report, toast } = useUi();
  const navigate = useNavigate();
  const { itemId = "" } = useParams();
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [retry, setRetry] = useState(0);
  const [range, setRange] = useState<DayRange | null>(null);
  const [busy, setBusy] = useState(false);
  const [today] = useState(() => new Date());
  // 불러온 상세는 공유 저장소에 둔다 (헤더 더보기 메뉴가 같은 값을 쓴다)
  const current = useCurrentItem(itemId);

  useEffect(() => {
    let alive = true;
    getItem(itemId)
      .then((item) => {
        if (!alive) return;
        setCurrentItem(item);
        setLoaded({ key: itemId, state: "ok" });
      })
      .catch((e: unknown) => {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login");
          return;
        }
        setLoaded({
          key: itemId,
          state: "error",
          status: e instanceof ApiError ? e.status : 0,
          message: e instanceof ApiError ? e.message : "물건 정보를 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [itemId, retry, navigate]);

  // 화면을 떠나면 공유 상세를 비운다
  useEffect(() => () => setCurrentItem(null), []);

  if (!loaded || loaded.key !== itemId || (loaded.state === "ok" && !current)) {
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
  if (loaded.state === "error") {
    // 404: 없는 물건·삭제·비공개·다른 동네·차단 (API 명세 B_020은 존재를 숨긴다)
    if (loaded.status === 404 || loaded.status === 400) {
      return (
        <EmptyState icon="grid" title="이 물건을 볼 수 없어요">
          삭제되었거나 비공개로 바뀐 물건일 수 있어요. 다른 물건을 찾아보세요.
        </EmptyState>
      );
    }
    return (
      <ErrorState title="물건 정보를 불러오지 못했어요" onRetry={() => setRetry((n) => n + 1)}>
        {loaded.message}
      </ErrorState>
    );
  }

  const item = current as ApiItemDetail;
  const cat = toCategoryId(item.category.code);
  const owner = item.owner.displayName ?? UNKNOWN_OWNER;
  const place = item.place.name ?? UNKNOWN_PLACE;
  const availFrom = parseIsoDate(item.availableStartDate);
  const availTo = parseIsoDate(item.availableEndDate);
  const selected = range ?? defaultRange(availFrom, availTo, today);
  const own = item.allowedActions.includes("EDIT");
  const isPublic = item.visibility === "PUBLIC";
  // TODO(C_008): 거래 신뢰 지표 API 연동 전까지 가입 시기만 실제 값
  const trust = { kept: 0, total: 0, done: 0, since: formatYearMonth(item.owner.joinedAt) };

  const onRequest = () => navigate(`/items/${item.id}/request`);
  const onEdit = () => navigate(`/register?itemId=${item.id}`);

  const applyVisibility = async (next: "PUBLIC" | "HIDDEN") => {
    setBusy(true);
    try {
      const updated = await changeItemVisibility(item.id, next, item.version);
      setCurrentItem(updated);
      toast(next === "HIDDEN" ? "공개를 중지했어요" : "다시 공개했어요");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "처리하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const toggleVisibility = () => {
    if (isPublic) {
      confirm({
        title: `${item.title}${objectParticle(item.title)} 공개 중지할까요?`,
        body: "검색과 새 요청이 멈춰요. 진행 중인 대여는 그대로 유지돼요.",
        confirm: "공개 중지",
        onConfirm: () => void applyVisibility("HIDDEN"),
      });
    } else {
      void applyVisibility("PUBLIC");
    }
  };

  return (
    <div className={c("detail")}>
      <div className={c("detail-two")}>
        <div className={c("detail-main")}>
          <PhotoSlider
            cat={cat}
            images={item.images.map((img) => img.contentUrl)}
            title={item.title}
            className={c("detail-photo")}
          />

          <div className={c("detail-head")}>
            <div className={c("detail-tags t-micro")}>
              <span>{item.category.name}</span>
              <span>· {formatAgo(item.createdAt)} 등록</span>
            </div>
            <h1 className={c("t-title-lg detail-title")}>{item.title}</h1>
            <Meta icon="check" strong>{`${formatMonthDay(availFrom)} – ${formatMonthDay(availTo)} 대여 가능`}</Meta>
          </div>

          {item.description ? <p className={c("t-body detail-desc")}>{item.description}</p> : null}

          <PlaceCard place={place} detail={item.place.guide} distance={item.distanceMeters === undefined ? undefined : formatDistance(item.distanceMeters)} />

          <section className={c("detail-cal")} aria-labelledby="cal-h">
            <SectionHead id="cal-h" title="대여 가능한 날짜" />
            <span className={c("t-caption detail-muted detail-cal-range")}>
              {`${availFrom.getMonth() + 1}/${availFrom.getDate()} – ${availTo.getMonth() + 1}/${availTo.getDate()}`}
            </span>
            {/* TODO(C_026): 예약 구간(booked)은 대여 담당 달력 API 연동 후 표시 */}
            <Calendar today={today} from={availFrom} to={availTo} value={selected} onChange={setRange} markAvailable />
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
                  <Meta icon={isPublic ? "check" : "lock"} strong>
                    {isPublic ? "공개 중 · 이웃이 찾을 수 있어요" : "공개 중지 · 검색과 새 요청이 멈춰 있어요"}
                  </Meta>
                  <Button block onClick={() => navigate("/rentals")}>요청 보기</Button>
                  <Button variant="secondary" block onClick={onEdit}>수정</Button>
                  <Button variant="ghost" block icon={isPublic ? "lock" : "check"} disabled={busy} onClick={toggleVisibility}>
                    {isPublic ? "공개 중지" : "다시 공개"}
                  </Button>
                </>
              ) : (
                <>
                  <span className={c("t-micro detail-muted")}>선택한 기간</span>
                  {selected ? (
                    <DateRange start={md(selected[0])} end={md(selected[1])} />
                  ) : (
                    <span className={c("t-label detail-conflict")}>대여 가능한 날짜가 지났어요</span>
                  )}
                  <Meta icon="pin">{place}</Meta>
                  <Button size="lg" block disabled={!selected} onClick={onRequest}>대여 요청</Button>
                  <Button variant="secondary" block icon="chat" onClick={() => navigate("/chat/c1")}>
                    {owner} 님에게 채팅으로 물어보기
                  </Button>
                  <span className={c("t-caption detail-muted")}>
                    요청은 무료예요. {owner} 님이 승인하면 알려드려요.
                  </span>
                </>
              )}
            </div>
          </div>
          <OwnerCard owner={owner} trust={trust} />
          {own ? null : (
          <button
            type="button"
            className={c("t-label detail-report")}
            onClick={() => report({ name: owner, onConfirm: () => toast("신고를 접수했어요") })}
          >
            이 물건 신고하기
          </button>
          )}
        </div>
      </div>

      <div className={c("detail-actionbar only-mobile")}>
        {own ? (
          <>
            <div className={c("detail-actionbar-info")}>
              <span className={c("t-label")}>내가 올린 물건이에요</span>
              <span className={c("t-caption detail-muted")}>{isPublic ? "공개 중" : "공개 중지"}</span>
            </div>
            <Button variant="secondary" onClick={onEdit}>수정</Button>
            <Button onClick={() => navigate("/rentals")}>요청 보기</Button>
          </>
        ) : (
          <>
            <div className={c("detail-actionbar-info")}>
              {selected ? (
                <DateRange start={md(selected[0])} end={md(selected[1])} icon={false} len={false} className={c("detail-actionbar-range")} />
              ) : null}
              <span className={c("t-caption detail-muted")}>무료 · {place}</span>
            </div>
            <button
              type="button"
              className={c("detail-chat-button")}
              aria-label={`${owner} 님과 채팅`}
              onClick={() => navigate("/chat/c1")}
            >
              <Icon name="chat" size={22} />
            </button>
            <Button size="lg" disabled={!selected} onClick={onRequest}>대여 요청</Button>
          </>
        )}
      </div>
    </div>
  );
}
