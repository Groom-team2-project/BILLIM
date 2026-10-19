import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { SearchField } from "@/components/ui/search-field";
import { StatusBadge } from "@/components/custom/rentals/StatusBadge";
import { Alert } from "@/components/ui/alert";

import { Meta } from "@/components/ui/meta";
import { SectionHead } from "@/components/ui/section-head";
import { CommunitySheet } from "@/components/custom/community/CommunitySheet";
import { useActiveCommunity } from "@/stores/community";
import { CategoryGrid } from "@/components/custom/items/CategoryGrid";
import { ItemCard } from "@/components/custom/items/ItemCard";
import { ApiError } from "@/api/client";
import { type ApiItemSummary, searchItems } from "@/api/items";
import { fillPlace, toCardItem } from "@/utils/itemView";
import { useCommunityPlaces } from "@/hooks/useCommunityPlaces";
import { getRentalSummary } from "@/api/rentals";
import type { ApiRentalSummary } from "@/types/rental-api";
import { summaryCells } from "@/utils/rentalOverview";
import { toRental } from "@/utils/rentalView";
import { Skeleton } from "@/components/ui/state";
import { STATUS_UI } from "@/components/custom/rentals/status";
import styles from "@/pages/home/HomePage.module.css";
import { classes } from "@/utils/classes";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";

const c = classes(styles);

/** 이웃 수(E) — 홈 조립 API 연동 시 교체 */
const COMMUNITY = { neighbors: 128 };

/** 홈 최신 물건 개수 */
const LATEST_SIZE = 6;

type Latest =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; items: ApiItemSummary[]; total: number };

export function HomePage() {
  const navigate = useNavigate();
  const activeName = useActiveCommunity();
  const [commOpen, setCommOpen] = useState(false);
  const [latest, setLatest] = useState<Latest>({ state: "loading" });
  const places = useCommunityPlaces();
  const [retry, setRetry] = useState(0);
  const [summary, setSummary] = useState<ApiRentalSummary | null>(null);
  const [summaryError, setSummaryError] = useState("");
  const [summaryRetry, setSummaryRetry] = useState(0);
  useEffect(() => {
    let alive = true;
    getRentalSummary().then((value) => { if (alive) setSummary(value); }).catch((e: unknown) => {
      if (!alive) return;
      if (e instanceof ApiError && e.unauthenticated) { navigate("/login"); return; }
      setSummaryError(e instanceof ApiError ? e.message : "대여 현황을 불러오지 못했어요.");
    });
    return () => { alive = false; };
  }, [summaryRetry, navigate]);
  const next = summary?.nextRentals[0];
  const urgent = next ? toRental(next) : null;

  // 최신 물건 (B_017 LATEST). TODO(B_027): 홈 조립 API가 생기면 교체
  useEffect(() => {
    let alive = true;
    searchItems({ sort: "LATEST", page: 0, size: LATEST_SIZE })
      .then((page) => alive && setLatest({ state: "ok", items: page.items, total: page.totalElements }))
      .catch((e: unknown) => {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login");
          return;
        }
        setLatest({ state: "error", message: e instanceof ApiError ? e.message : "물건을 불러오지 못했어요." });
      });
    return () => {
      alive = false;
    };
  }, [retry, navigate]);
  return (
    <div className={c("home")}>
      <div className={c("home-head")}>
        <div className={c("home-community-row")}>
          {/* 내 동네 설정 시트 — 최대 2개, 전환·삭제·추가 (당근마켓식) */}
          <button
            type="button"
            className={c("home-community")}
            aria-label={`내 동네 설정, 현재 ${activeName}`}
            onClick={() => setCommOpen(true)}
          >
            <Icon name="pin" size={14} />
            {activeName}
          </button>
          <span className={c("t-caption home-muted")}>
            이웃 {COMMUNITY.neighbors}명{latest.state === "ok" ? ` · 물건 ${latest.total}개` : ""}
          </span>
        </div>
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
          {latest.state === "loading" ? (
            <Loading label="물건 불러오는 중">
              <div className={c("home-grid")}>
                {[0, 1, 2, 3].map((i) => (
                  <SkeletonItem key={i} />
                ))}
              </div>
            </Loading>
          ) : latest.state === "error" ? (
            <ErrorState title="물건을 불러오지 못했어요" onRetry={() => {
              setLatest({ state: "loading" });
              setRetry((n) => n + 1);
            }}>
              {latest.message}
            </ErrorState>
          ) : latest.items.length === 0 ? (
            <EmptyState icon="grid" title="아직 등록된 물건이 없어요">
              우리 동네 첫 물건을 올려 보세요. 가끔 쓰는 물건이면 충분해요.
            </EmptyState>
          ) : (
            <div className={c("home-grid")}>
              {latest.items.map((item) => (
                <ItemCard key={item.id} item={toCardItem(fillPlace(item, places))} />
              ))}
            </div>
          )}
        </section>

        <div className={c("home-aside")}>
          <section className={c("home-card")} aria-labelledby="my-rentals">
            <SectionHead id="my-rentals" title="내 대여 현황" more="전체 보기" moreTo="/rentals" />
            {summaryError ? <ErrorState title="대여 현황을 불러오지 못했어요" onRetry={() => { setSummaryError(""); setSummaryRetry((n) => n + 1); }}>{summaryError}</ErrorState>
              : !summary ? <Loading label="대여 현황 불러오는 중"><Skeleton h={64} /></Loading>
              : <>
            <div className={c("home-summary")}>
              {summaryCells(summary).map((cell) => (
                <Link key={cell.status} to="/rentals" className={c(`home-summary-cell home-summary-cell--${cell.status}`)}>
                  <span className={c("t-micro home-summary-label")}>
                    <Icon name={STATUS_UI[cell.status].icon} size={13} />{cell.label}
                  </span>
                  <span className={c("home-summary-count")}>{cell.count}</span>
                  <span className={c("t-micro home-muted")}>{cell.note}</span>
                </Link>
              ))}
            </div>
            {next && urgent ? <Link to={`/rentals?rentalId=${encodeURIComponent(next.id)}`} className={c("home-urgent")}>
              <StatusBadge status={urgent.status} />
              <div className={c("home-urgent-body")}>
                <div className={c("t-label")}>{next.itemTitle} · {urgent.dday}</div>
                <Meta icon="pin">{next.placeName}</Meta>
              </div>
            </Link> : <p className={c("t-caption home-muted")}>{Object.values(summary.borrowed).every((count) => count === 0) && Object.values(summary.lent).every((count) => count === 0) ? "아직 빌리거나 빌려준 물건이 없어요." : "예정된 전달·반납이 없어요."}</p>}
            </>}

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
