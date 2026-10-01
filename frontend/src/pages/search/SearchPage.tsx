import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { Button } from "@/components/ui/button";
import { Calendar, type DayRange } from "@/components/ui/calendar";
import { Chip } from "@/components/ui/chip";
import { SearchField } from "@/components/ui/search-field";
import { Segmented } from "@/components/ui/segmented";
import { Alert } from "@/components/ui/alert";
import { ItemCard } from "@/components/custom/items/ItemCard";
import { MapView } from "@/components/custom/items/MapView";
import { CATEGORIES, CATEGORY_MAP, MAP_PINS, MOCK_ITEMS } from "@/data/items";
import type { CategoryId } from "@/data/items";
import { fmtDate, md } from "@/utils/date";
import styles from "@/pages/search/SearchPage.module.css";
import overlayStyles from "@/components/overlay/overlay.module.css";
import { classes } from "@/utils/classes";
import { MOCK_TODAY } from "@/constants/mock";
import { ChipScroller } from "@/components/ui/chip-scroller";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { useScreenState } from "@/hooks/useScreenState";

const c = classes({ ...styles, ...overlayStyles });


function isCategoryId(v: string | null): v is CategoryId {
  return !!v && CATEGORIES.some((x) => x.id === v);
}

export function SearchPage() {
  const [params] = useSearchParams();
  const paramCat = params.get("cat");
  const [cat, setCat] = useState<CategoryId | null>(isCategoryId(paramCat) ? paramCat : "camp");
  const [period, setPeriod] = useState<DayRange | null>([new Date(2026, 8, 26), new Date(2026, 8, 27)]);
  const [picker, setPicker] = useState<"period" | "category" | null>(null);
  const [view, setView] = useState<"list" | "map">("list");
  const [sort, setSort] = useState<"new" | "near">("near");
  const [pinId, setPinId] = useState("a");
  const state = useScreenState();

  const pin = MAP_PINS.find((p) => p.id === pinId) ?? MAP_PINS[0];
  const pinItems = MOCK_ITEMS.filter((i) => i.place.includes(pin.label.split(" ")[0])).slice(0, 2);

  const results = MOCK_ITEMS.filter((i) => (cat ? i.cat === cat : true));
  const dateLabel = period ? `${fmtDate(md(period[0]))} – ${fmtDate(md(period[1]))}` : "대여 기간";
  const catLabel = cat ? CATEGORY_MAP[cat].label : "카테고리";

  const clearAll = () => {
    setCat(null);
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
          {CATEGORIES.map((co) => (
            <Chip key={co.id} icon={co.icon} selected={co.id === cat} onClick={() => setCat(cat === co.id ? null : co.id)}>
              {co.label}
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
            <SearchField />
            <ChipScroller label="필터">
              <Chip icon="calendar" selected={!!period} caret onClick={() => setPicker("period")}>
                {dateLabel}
              </Chip>
              <Chip selected={!!cat} caret onClick={() => setPicker("category")}>
                {catLabel}
              </Chip>
              <Chip icon="filter" onClick={() => setPicker("category")}>필터</Chip>
            </ChipScroller>
          </div>
          <div className={c("only-desk")}>
            <SearchField />
          </div>

          <div className={c("search-toolbar")}>
            <p className={c("t-label search-count")} aria-live="polite">
              결과 {results.length}개<span className={c("search-muted")}> · 새솔마을 5단지</span>
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
              <MapView selected={pinId} height={380} onSelect={setPinId} />
              <div className={c("search-map-place")}>
                <div className={c("t-caption search-muted search-map-place-head")}>
                  {pin.label} · 물건 {pin.n}개
                </div>
                {pinItems.length === 0 ? (
                  <p className={c("t-caption search-muted search-empty")}>이 장소에 등록된 물건이 없어요.</p>
                ) : (
                  pinItems.map((i) => <ItemCard key={i.id} item={i} layout="row" range={!!period} />)
                )}
              </div>
            </div>
          ) : (
            <div className={c("search-list")}>
              {state === "loading" ? (
                <Loading label="검색 결과 불러오는 중">
                  {[0, 1, 2].map((i) => (
                    <SkeletonItem key={i} layout="row" />
                  ))}
                </Loading>
              ) : state === "error" ? (
                <ErrorState title="검색 결과를 불러오지 못했어요" />
              ) : state === "empty" || results.length === 0 ? (
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
                  {results.map((item) => <ItemCard key={item.id} item={item} layout="row" range={!!period} />)}
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
            <Calendar today={MOCK_TODAY} value={period} onChange={setPeriod} />
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
              <Chip selected={!cat} onClick={() => { setCat(null); setPicker(null); }}>전체</Chip>
              {CATEGORIES.map((co) => (
                <Chip
                  key={co.id}
                  icon={co.icon}
                  selected={co.id === cat}
                  onClick={() => {
                    setCat(co.id);
                    setPicker(null);
                  }}
                >
                  {co.label}
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
