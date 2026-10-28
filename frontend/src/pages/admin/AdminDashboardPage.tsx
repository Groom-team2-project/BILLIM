import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { BarsCat, BarsWeek, StatTile } from "@/components/custom/admin/charts";
import { ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { SearchField } from "@/components/ui/search-field";
import { ApiError } from "@/api/client";
import { type ApiCommunity, searchCommunities } from "@/api/communities";
import { getAdminReports } from "@/api/reports";
import { type ApiAdminStats, getAdminStatistics } from "@/api/statistics";
import adminStyles from "@/components/custom/admin/admin.module.css";
import { classes } from "@/utils/classes";

const c = classes(adminStyles);

/** 기간 기본값. 프로토타입의 '최근 7일'·'주간 대여 건수'에 맞춘다 */
const PERIOD_DAYS = 7;

const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];

/**
 * KST 달력 날짜(YYYY-MM-DD). 서버가 from/to를 KST로 해석하므로 보는 사람의 시간대와
 * 무관하게 KST로 계산한다. (API 명세 13.6)
 */
function kstDate(offsetDays: number): string {
  const shifted = Date.now() + 9 * 60 * 60 * 1000 + offsetDays * 24 * 60 * 60 * 1000;
  return new Date(shifted).toISOString().slice(0, 10);
}

/** 'YYYY-MM-DD'의 요일. Date.UTC로 만들어 시간대에 따라 하루 밀리지 않게 한다 */
function weekdayLabel(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return WEEKDAY[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

type Stats =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; data: ApiAdminStats };

export function AdminDashboardPage() {
  const navigate = useNavigate();
  const [communities, setCommunities] = useState<ApiCommunity[]>([]);
  const [communityId, setCommunityId] = useState("");
  const [stats, setStats] = useState<Stats>({ state: "loading" });
  const [pendingAll, setPendingAll] = useState<number | null>(null);
  const [retry, setRetry] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [totalCommunities, setTotalCommunities] = useState(0);

  const from = kstDate(-(PERIOD_DAYS - 1));
  const to = kstDate(0);

  // 위치 인증이 동네를 자동 등록해 전국 동네가 쌓인다. 전부 버튼으로 깔 수 없어 검색으로 좁힌다
  useEffect(() => {
    const id = setTimeout(() => setQuery(keyword.trim()), 300);
    return () => clearTimeout(id);
  }, [keyword]);

  useEffect(() => {
    let alive = true;
    searchCommunities(query || undefined)
      .then((page) => {
        if (!alive) return;
        setCommunities(page.items);
        setTotalCommunities(page.totalElements);
        // 검색 결과가 바뀌면 고른 동네가 목록에 없을 수 있다. 첫 동네로 되돌린다
        setCommunityId((prev) =>
          page.items.some((community) => community.id === prev) ? prev : (page.items[0]?.id ?? ""),
        );
        if (page.items.length === 0) {
          setStats({
            state: "error",
            message: query ? "검색 결과가 없어요." : "등록된 동네가 없어요.",
          });
        }
      })
      .catch((e: unknown) => {
        if (!alive) return;
        setStats({
          state: "error",
          message: e instanceof ApiError ? e.message : "동네 목록을 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [query]);

  useEffect(() => {
    if (!communityId) return;
    let alive = true;
    getAdminStatistics(communityId, from, to)
      .then((data) => alive && setStats({ state: "ok", data }))
      .catch((e: unknown) => {
        if (!alive) return;
        setStats({
          state: "error",
          message: e instanceof ApiError ? e.message : "통계를 불러오지 못했어요.",
        });
      });
    return () => {
      alive = false;
    };
  }, [communityId, from, to, retry]);

  // 버튼의 미처리 건수는 동네를 가리지 않는 전체다. 목록 총건수만 쓰므로 1건만 받는다
  useEffect(() => {
    let alive = true;
    getAdminReports("PENDING", undefined, 0, 1)
      .then((page) => alive && setPendingAll(page.totalElements))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [retry]);

  const selected = communities.find((community) => community.id === communityId);

  /** 동네를 바꾸면 이전 동네 수치가 남지 않게 바로 로딩으로 돌린다 */
  const selectCommunity = (id: string) => {
    if (id === communityId) return;
    setStats({ state: "loading" });
    setCommunityId(id);
  };

  const retryLoad = () => {
    setStats({ state: "loading" });
    setRetry((n) => n + 1);
  };

  return (
    <div className={c("adm-body")}>
      <div>
        <p className={c("t-label adm-muted adm-section-label")}>동네 선택</p>
        <SearchField
          placeholder="동네 이름으로 찾기"
          label="동네 검색"
          value={keyword}
          onChange={setKeyword}
        />
        {totalCommunities > communities.length && (
          <p className={c("t-caption adm-muted")}>
            동네 {totalCommunities.toLocaleString()}곳 중 {communities.length}곳만 보여요. 이름으로 찾아 주세요.
          </p>
        )}
      </div>

      {communities.length > 0 && (
        <div>
          <div className={c("adm-comm")}>
            {communities.map((community) => (
              <button
                key={community.id}
                type="button"
                className={c(`adm-comm-b${communityId === community.id ? " adm-comm-b--on" : ""}`)}
                onClick={() => selectCommunity(community.id)}
              >
                {community.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {stats.state === "loading" && (
        <Loading label="통계를 불러오는 중">
          <div className={c("adm-kpis")}>
            <SkeletonItem />
            <SkeletonItem />
            <SkeletonItem />
          </div>
        </Loading>
      )}

      {stats.state === "error" && (
        <ErrorState onRetry={retryLoad}>{stats.message}</ErrorState>
      )}

      {stats.state === "ok" && (
        <>
          <div className={c("adm-kpis")}>
            <StatTile label="등록 물건" value={String(stats.data.registeredItems)} sub="전체 누적" />
            <StatTile label="활성 대여" value={String(stats.data.activeRentals)} sub="진행 중" />
            <StatTile label="최근 7일 신규" value={`${stats.data.newItems}개`} sub="물건" />
            <StatTile label="신규 이웃" value={String(stats.data.newMembers)} sub="최근 7일" />
            <StatTile label="대여 성사율" value={`${stats.data.successRate}%`} sub="요청 대비" />
            <StatTile
              label="미처리 신고"
              value={String(stats.data.pendingReports)}
              sub={stats.data.pendingReports > 0 ? "확인 필요" : "모두 처리됨"}
              alert={stats.data.pendingReports > 0}
            />
          </div>

          <BarsWeek
            title={`주간 대여 요청 · ${selected?.name ?? ""}`}
            data={stats.data.dailyRentalCounts.map((day) => [weekdayLabel(day.date), day.count])}
          />
          <BarsCat
            title="카테고리별 등록 물건 (10개 분류)"
            data={stats.data.categoryCounts.map((row) => [row.category.name, row.count])}
          />

          <div className={c("adm-card")}>
            <p className={c("t-label adm-chart-title")}>인기 물건 TOP 3</p>
            {stats.data.popularItems.length === 0 ? (
              <p className={c("t-caption adm-muted")}>최근 7일에 반납 완료된 거래가 없어요.</p>
            ) : (
              <div className={c("adm-pop-list")}>
                {stats.data.popularItems.map((item, index) => (
                  <div key={item.itemId} className={c("adm-pop-row")}>
                    <span className={c("adm-rank")}>{index + 1}</span>
                    <span className={c("adm-pop-title")}>{item.title}</span>
                    <span className={c("t-caption adm-muted")}>{item.returnedCount}회 대여</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      <Button variant="secondary" block onClick={() => navigate("/admin/reports")}>
        신고 관리로 이동{pendingAll === null ? "" : ` (미처리 ${pendingAll}건)`}
      </Button>
      <p className={c("t-caption adm-muted")}>
        등록 물건·활성 대여·미처리 신고는 지금까지의 누적이고, 신규·성사율·주간 그래프는 최근 7일({from} ~ {to})
        기준이에요. 성사율은 아직 진행 중인 요청도 분모에 들어가요.
      </p>
    </div>
  );
}
