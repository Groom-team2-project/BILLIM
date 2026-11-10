import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { SearchField } from "@/components/ui/search-field";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { Alert } from "@/components/ui/alert";
import { DDay } from "@/components/ui/d-day";
import { Meta } from "@/components/ui/meta";
import { SectionHead } from "@/components/ui/section-head";
import { CommunitySheet } from "@/components/custom/community/CommunitySheet";
import { CategoryGrid } from "@/components/custom/items/CategoryGrid";
import { ItemCard } from "@/components/custom/items/ItemCard";
import { ApiError } from "@/api/client";
import { type ApiHome, getHome } from "@/api/home";
import { type ApiNotice, getCommunityNotices } from "@/api/notices";
import { NoticeBar } from "@/components/custom/notices";
import { fillPlace, toCardItem } from "@/utils/itemView";
import { useCommunityPlaces } from "@/hooks/useCommunityPlaces";
import { STATUS_UI } from "@/components/custom/rentals/status";
import { toRentalSummaryCells, toUpcomingRental } from "@/utils/homeView";
import styles from "@/pages/home/HomePage.module.css";
import { classes } from "@/utils/classes";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";

const c = classes(styles);

const EMPTY_COUNTS = { requested: 0, approved: 0, active: 0, overdue: 0, returned: 0, rejected: 0, canceled: 0, expired: 0 };

type Home =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; data: ApiHome };

export function HomePage() {
  const navigate = useNavigate();
  const [commOpen, setCommOpen] = useState(false);
  const [home, setHome] = useState<Home>({ state: "loading" });
  const places = useCommunityPlaces();
  const [retry, setRetry] = useState(0);

  // 홈 화면 조립 (B_027) — 동네·최신 물건·물건 수·내 대여 요약을 한 번에
  useEffect(() => {
    let alive = true;
    getHome()
      .then((data) => alive && setHome({ state: "ok", data }))
      .catch((e: unknown) => {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login");
          return;
        }
        setHome({ state: "error", message: e instanceof ApiError ? e.message : "홈 정보를 불러오지 못했어요." });
      });
    return () => {
      alive = false;
    };
  }, [retry, navigate]);

  const data = home.state === "ok" ? home.data : null;
  const communityId = data?.community.id;
  const [notice, setNotice] = useState<ApiNotice | null>(null);

  // 동네 공지 첫 줄 (E_063) — 고정 우선·최신순 1건. 실패해도 홈은 그대로라 표시만 생략
  useEffect(() => {
    if (!communityId) return;
    let alive = true;
    getCommunityNotices(communityId, 0, 1)
      .then((page) => alive && setNotice(page.items[0] ?? null))
      .catch(() => alive && setNotice(null));
    return () => {
      alive = false;
    };
  }, [communityId]);

  // 홈 응답 전에는 이름 대신 자리 문구
  const communityName = data?.community.name ?? "내 동네";
  const summary = toRentalSummaryCells(
    data?.myRentals ?? { borrowed: EMPTY_COUNTS, lent: EMPTY_COUNTS, nextRentals: [] },
  );
  const upcoming = data ? toUpcomingRental(data.myRentals.nextRentals) : null;
  return (
    <div className={c("home")}>
      <div className={c("home-head")}>
        <div className={c("home-community-row")}>
          {/* 내 동네 설정 시트 — 최대 2개, 전환·삭제·추가 (당근마켓식) */}
          <button
            type="button"
            className={c("home-community")}
            aria-label={`내 동네 설정, 현재 ${communityName}`}
            onClick={() => setCommOpen(true)}
          >
            <Icon name="pin" size={14} />
            {communityName}
          </button>
          {data ? (
            <span className={c("t-caption home-muted")}>
              이웃 {data.community.verifiedMemberCount}명 · 물건 {data.itemCount}개
            </span>
          ) : null}
        </div>
        {data && notice ? <NoticeBar notice={notice} /> : null}
        <h1 className={c("t-display home-greeting")}>
          잠깐 필요한 물건,
          <br />
          이웃에게 빌려요
        </h1>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const keyword = (e.currentTarget.querySelector("input")?.value ?? "").trim();
            navigate(keyword ? `/search?keyword=${encodeURIComponent(keyword)}` : "/search");
          }}
        >
          <SearchField />
        </form>
      </div>

      <section aria-label="카테고리">
        <CategoryGrid />
      </section>

      <div className={c("home-two")}>
        <section className={c("home-latest")}>
          <SectionHead title="최신 물건" more="더 보기" moreTo="/search" />
          {home.state === "loading" ? (
            <Loading label="물건 불러오는 중">
              <div className={c("home-grid")}>
                {[0, 1, 2, 3].map((i) => (
                  <SkeletonItem key={i} />
                ))}
              </div>
            </Loading>
          ) : home.state === "error" ? (
            <ErrorState title="물건을 불러오지 못했어요" onRetry={() => {
              setHome({ state: "loading" });
              setRetry((n) => n + 1);
            }}>
              {home.message}
            </ErrorState>
          ) : home.data.latestItems.length === 0 ? (
            <EmptyState icon="grid" title="아직 등록된 물건이 없어요">
              우리 동네 첫 물건을 올려 보세요. 가끔 쓰는 물건이면 충분해요.
            </EmptyState>
          ) : (
            <div className={c("home-grid")}>
              {home.data.latestItems.map((item) => (
                <ItemCard key={item.id} item={toCardItem(fillPlace(item, places))} />
              ))}
            </div>
          )}
        </section>

        <div className={c("home-aside")}>
          <section className={c("home-card")} aria-labelledby="my-rentals">
            <SectionHead id="my-rentals" title="내 대여 현황" more="전체 보기" moreTo="/rentals" />
            <div className={c("home-summary")}>
              {summary.map((cell) => (
                <Link key={cell.status} to="/rentals" className={c(`home-summary-cell home-summary-cell--${cell.status}`)}>
                  <span className={c("t-micro home-summary-label")}>
                    <Icon name={STATUS_UI[cell.status].icon} size={13} />
                    {cell.label}
                  </span>
                  <span className={c("home-summary-count")}>{data ? cell.count : "–"}</span>
                  <span className={c("t-micro home-muted")}>{cell.note}</span>
                </Link>
              ))}
            </div>
            {upcoming ? (
              <div className={c("home-urgent")}>
                <StatusBadge status={upcoming.status} />
                <div className={c("home-urgent-body")}>
                  <div className={c("t-label")}>{upcoming.title}</div>
                  <Meta icon="pin">{upcoming.detail}</Meta>
                </div>
                <DDay late={upcoming.late}>{upcoming.dDay}</DDay>
              </div>
            ) : null}
          </section>

          <Alert tone="info" title="거래는 공용 장소에서">
            무인택배함, 경비실 옆처럼 모두가 아는 곳에서 주고받아요. 집 주소는 묻지 않아요.
          </Alert>
        </div>
      </div>

      {commOpen ? <CommunitySheet onClose={() => setCommOpen(false)} /> : null}
    </div>
  );
}
