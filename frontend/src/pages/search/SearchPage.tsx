import { type FormEvent, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Chip } from "@/components/ui/chip";
import { SearchField } from "@/components/ui/search-field";
import { Segmented } from "@/components/ui/segmented";
import { Alert } from "@/components/ui/alert";
import { ItemCard } from "@/components/custom/items/ItemCard";
import { MapView } from "@/components/custom/items/MapView";
import { ApiError } from "@/api/client";
import { type ApiItemSummary, searchItems } from "@/api/items";
import { useCategories } from "@/hooks/useCategories";
import { useActiveCommunity } from "@/stores/community";
import { categoryIcon, toIsoDate } from "@/utils/registerForm";
import { formatMonthDay, toCardItem, toMapPins, toSearchQuery } from "@/utils/itemView";
import styles from "@/pages/search/SearchPage.module.css";
import overlayStyles from "@/components/overlay/overlay.module.css";
import { classes } from "@/utils/classes";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";

const c = classes({ ...styles, ...overlayStyles });


const PAGE_SIZE = 20;

type Result =
  | { key: string; state: "ok"; items: ApiItemSummary[]; total: number; hasNext: boolean; page: number }
  | { key: string; state: "error"; message: string };

export function SearchPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const categories = useCategories();
  const community = useActiveCommunity();
  const [keyword, setKeyword] = useState(params.get("keyword") ?? "");
  const [query, setQuery] = useState(keyword);
  const [categoryId, setCategoryId] = useState<string | null>(params.get("categoryId"));
  const [period, setPeriod] = useState<DayRange | null>(null);
  const [picker, setPicker] = useState<"period" | "category" | null>(null);
  const [view, setView] = useState<"list" | "map">("list");
  const [sort, setSort] = useState<"new" | "near">("new");
  const [pinId, setPinId] = useState<string | null>(null);
  const [result, setResult] = useState<Result | null>(null);
  const [moreLoading, setMoreLoading] = useState(false);
  const [retry, setRetry] = useState(0);
  const [today] = useState(() => new Date());

  // 입력이 멈춘 뒤 검색 (300ms)
  useEffect(() => {
    const t = setTimeout(() => setQuery(keyword), 300);
    return () => clearTimeout(t);
  }, [keyword]);

  const form = { keyword: query, categoryId, period, sort, page: 0, size: PAGE_SIZE };
  const key = JSON.stringify(toSearchQuery(form, toIsoDate));
  const loading = result?.key !== key;

  useEffect(() => {
    let alive = true;
    const q = JSON.parse(key) as ReturnType<typeof toSearchQuery>;
    searchItems(q)
      .then((page) => {
        if (alive) setResult({ key, state: "ok", items: page.items, total: page.totalElements, hasNext: page.hasNext, page: 0 });
      })
      .catch((e: unknown) => {
        if (!alive) return;
        if (e instanceof ApiError && e.unauthenticated) {
          navigate("/login");
          return;
        }
        setResult({ key, state: "error", message: e instanceof ApiError ? e.message : "검색 결과를 불러오지 못했어요." });
      });
    return () => {
      alive = false;
    };
  }, [key, retry, navigate]);

  const loadMore = async () => {
    if (!result || result.state !== "ok" || !result.hasNext) return;
    setMoreLoading(true);
    try {
      const next = await searchItems(toSearchQuery({ ...form, page: result.page + 1 }, toIsoDate));
      setResult({ ...result, items: [...result.items, ...next.items], hasNext: next.hasNext, page: result.page + 1 });
    } catch (e) {
      setResult({ key, state: "error", message: e instanceof ApiError ? e.message : "검색 결과를 불러오지 못했어요." });
    } finally {
      setMoreLoading(false);
    }
  };

  // 검색어 입력(공통 SearchField는 값 속성이 없어 입력 이벤트로 받는다)
  const fieldRef = (el: HTMLDivElement | null) => {
    const input = el?.querySelector("input");
    if (input && document.activeElement !== input && input.value !== keyword) input.value = keyword;
  };
  const onFieldInput = (e: FormEvent<HTMLDivElement>) => setKeyword((e.target as HTMLInputElement).value);

  const items = result?.state === "ok" && !loading ? result.items : [];
  const pins = toMapPins(items);
  const pin = pins.find((p) => p.id === pinId) ?? pins[0];
  const pinItems = pin ? items.filter((i) => i.place.id === pin.id) : [];

  const selectedCategory = categories?.find((x) => x.id === categoryId);
  const dateLabel = period ? `${formatMonthDay(period[0])} – ${formatMonthDay(period[1])}` : "대여 기간";
  const catLabel = selectedCategory ? selectedCategory.name : "카테고리";

  const clearAll = () => {
    setCategoryId(null);
    setPeriod(null);
  };

  const sideFilters = (
    <aside className={c("search-filters")} aria-label="필터">
      <div className={c("search-filter-group")}>
        <span className={c("t-label")}>대여 기간</span>
        <Chip icon="calendar" selected={!!period} caret onClick={() => setPicker("period")}>
          {dateLabel}
        </Chip>
        {period ? <span className={c("t-caption search-muted")}>이 기간에 비어 있는 물건만 보여요</span> : null}
      </div>
      <div className={c("search-filter-group")}>
        <span className={c("t-label")}>카테고리</span>
        <div className={c("search-filter-chips")}>
          {(categories ?? []).map((co) => (
            <Chip
              key={co.id}
              icon={categoryIcon(co.code)}
              selected={co.id === categoryId}
              onClick={() => setCategoryId(categoryId === co.id ? null : co.id)}
            >
              {co.name}
            </Chip>
          ))}
        </div>
      </div>
      <Button variant="ghost" size="sm" icon="refresh" onClick={clearAll}>필터 초기화</Button>
    </aside>
  );

  return (
    <div className={c("search")}>
      <h1 className={c("t-title-lg only-desk search-title")}>물건 찾기</h1>
      <div className={c("search-two")}>
        <div className={c("only-desk")}>{sideFilters}</div>

        <div className={c("search-main")}>
          <div className={c("only-mobile search-mobile-filters")}>
            <div ref={fieldRef} onInput={onFieldInput}>
              <SearchField />
            </div>
            <ChipScroller label="필터">
              <Chip icon="calendar" selected={!!period} caret onClick={() => setPicker("period")}>
                {dateLabel}
              </Chip>
              <Chip selected={!!categoryId} caret onClick={() => setPicker("category")}>
                {catLabel}
              </Chip>
              {/* 선택한 필터가 있을 때만 "필터 초기화" 칩을 보여 준다 */}
              {period || categoryId ? (
                <Chip icon="refresh" onClick={clearAll}>필터 초기화</Chip>
              ) : null}
            </ChipScroller>
          </div>
          <div className={c("only-desk")} ref={fieldRef} onInput={onFieldInput}>
            <SearchField />
          </div>

          <div className={c("search-toolbar")}>
            <p className={c("t-label search-count")} aria-live="polite">
              결과 {result?.state === "ok" && !loading ? result.total : 0}개
              <span className={c("search-muted")}> · {community}</span>
            </p>
            <div className={c("search-toolbar-controls")}>
              <Segmented
                label="정렬"
                value={sort}
                onChange={setSort}
                options={[
                  { value: "new", label: "최신순" },
                  { value: "near", label: "가까운 순" },
                ]}
              />
              <Segmented
                label="보기"
                value={view}
                onChange={setView}
                options={[
                  { value: "list", label: "목록", icon: "list" },
                  { value: "map", label: "지도", icon: "map" },
                ]}
              />
            </div>
          </div>

          {view === "map" ? (
            <div className={c("search-map")}>
              <MapView selected={pin?.id} height={380} onSelect={setPinId} pins={pins} />
              <div className={c("search-map-place")}>
                {pin ? (
                  <div className={c("t-caption search-muted search-map-place-head")}>
                    {pin.label} · 물건 {pin.n}개
                  </div>
                ) : null}
                {pinItems.length === 0 ? (
                  <p className={c("t-caption search-muted search-empty")}>이 장소에 등록된 물건이 없어요.</p>
                ) : (
                  pinItems.map((i) => <ItemCard key={i.id} item={toCardItem(i)} layout="row" range={!!period} />)
                )}
              </div>
            </div>
          ) : (
            <div className={c("search-list")}>
              {loading ? (
                <Loading label="검색 결과 불러오는 중">
                  {[0, 1, 2].map((i) => (
                    <SkeletonItem key={i} layout="row" />
                  ))}
                </Loading>
              ) : result?.state === "error" ? (
                <ErrorState title="검색 결과를 불러오지 못했어요" onRetry={() => setRetry((n) => n + 1)}>
                  {result.message}
                </ErrorState>
              ) : items.length === 0 ? (
                <EmptyState
                  icon="search"
                  title="조건에 맞는 물건이 없어요"
                  actions={<Button variant="secondary" icon="refresh" onClick={clearAll}>필터 초기화</Button>}
                >
                  기간이나 카테고리를 바꾸면 더 많은 물건을 볼 수 있어요.
                </EmptyState>
              ) : (
                <>
                  <Alert tone="info">
                    가능 여부는 지금 기준이에요. 요청 후 소유자가 승인할 때 한 번 더 확인해요.
                  </Alert>
                  {items.map((item) => <ItemCard key={item.id} item={toCardItem(item)} layout="row" range={!!period} />)}
                  {result?.state === "ok" && result.hasNext ? (
                    <Button variant="secondary" block disabled={moreLoading} onClick={() => void loadMore()}>
                      {moreLoading ? "불러오는 중…" : "더 보기"}
                    </Button>
                  ) : null}
                </>
              )}
            </div>
          )}

          <p className={c("t-caption search-muted search-note")}>
            <Icon name="info" size={14} />
            거리는 커뮤니티 공용 장소 기준이에요
          </p>
        </div>
      </div>

      {picker === "period" ? (
        <div className={c("ui-scrim")} onClick={() => setPicker(null)}>
          <div className={c("ui-modal ui-modal--wide")} role="dialog" aria-modal="true" aria-label="대여 기간 선택" onClick={(e) => e.stopPropagation()}>
            <h3 className={c("ui-modal-title")}>대여 기간</h3>
            <p className={c("ui-modal-body")}>시작일과 반납일을 골라 주세요. 두 날 모두 대여 기간에 포함돼요.</p>
            <Calendar today={today} value={period} onChange={setPeriod} />
            <div className={c("ui-modal-actions")}>
              <Button
                variant="secondary"
                onClick={() => {
                  setPeriod(null);
                  setPicker(null);
                }}
              >
                기간 지우기
              </Button>
              <Button onClick={() => setPicker(null)}>적용</Button>
            </div>
          </div>
        </div>
      ) : null}

      {picker === "category" ? (
        <div className={c("ui-scrim")} onClick={() => setPicker(null)}>
          <div className={c("ui-modal ui-modal--wide")} role="dialog" aria-modal="true" aria-label="카테고리 선택" onClick={(e) => e.stopPropagation()}>
            <h3 className={c("ui-modal-title")}>카테고리</h3>
            <div className={c("search-cat-pick")}>
              <Chip selected={!categoryId} onClick={() => { setCategoryId(null); setPicker(null); }}>전체</Chip>
              {(categories ?? []).map((co) => (
                <Chip
                  key={co.id}
                  icon={categoryIcon(co.code)}
                  selected={co.id === categoryId}
                  onClick={() => {
                    setCategoryId(co.id);
                    setPicker(null);
                  }}
                >
                  {co.name}
                </Chip>
              ))}
            </div>
            <Button
              variant="ghost"
              size="sm"
              icon="refresh"
              onClick={() => {
                clearAll();
                setPicker(null);
              }}
            >
              필터 초기화
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
