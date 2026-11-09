import { type KeyboardEvent, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import {
  type ApiCommunity,
  type RegionLevel,
  type RegionScope,
  getRegionOptions,
  searchCommunities,
} from "@/api/communities";
import styles from "@/components/custom/community/RegionPicker.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 한 번에 보여주는 동네 수. 한 읍·면·동에 동네가 여럿일 수 있다 */
const LIST_SIZE = 50;

type Levels = { sido: string; sigungu: string; gu: string; dong: string };

const EMPTY: Levels = { sido: "", sigungu: "", gu: "", dong: "" };

/** 단계 옵션·목록 요청이 지금 고른 범위의 것인지 가리는 키 */
function scopeKey(levels: Levels): string {
  return `${levels.sido}|${levels.sigungu}|${levels.gu}|${levels.dong}`;
}

/** 목록을 띄우려면 아직 무엇을 더 골라야 하는지 */
function nextStepLabel(levels: Levels, showGu: boolean): string {
  if (!levels.sido) return "시·도를 골라 주세요.";
  if (!levels.sigungu) return "시·군·구까지 골라 주세요.";
  if (showGu && !levels.gu) return "구까지 골라 주세요.";
  return "동네를 불러오는 중…";
}

/** 빈 단계는 보내지 않는다. 서버에서 빈 값은 "전체"다 */
function toScope(levels: Levels): RegionScope {
  return {
    sido: levels.sido || undefined,
    sigungu: levels.sigungu || undefined,
    gu: levels.gu || undefined,
    dong: levels.dong || undefined,
  };
}

/** 단계 옵션. `loaded`로 "불러오는 중"과 "그 단계가 없음"을 구분한다 */
type Options = { items: string[]; loaded: boolean };

/**
 * 단계 옵션 조회 (E_073).
 *
 * 상위가 비면 부르지 않는다. 결과는 요청한 범위의 키와 함께 담아, 상위가 바뀌면 이전 결과가
 * 남지 않게 한다. `loaded`를 함께 주는 이유는 빈 목록이 두 가지 뜻이기 때문이다 —
 * 아직 안 받았거나, 그 지역에 그 단계가 없거나. 둘을 섞으면 단계가 잠깐 열렸다 닫힌다.
 */
function useRegionOptions(
  level: RegionLevel,
  levels: Levels,
  ready: boolean,
  membersOnly: boolean,
): Options {
  const [done, setDone] = useState<{ key: string; items: string[] }>({ key: "", items: [] });
  const { sido, sigungu, gu } = levels;
  // 읍·면·동 옵션은 읍·면·동 선택과 무관하다. 고른 값까지 키에 넣으면 고를 때마다 다시 부른다
  const key = scopeKey({ sido, sigungu, gu, dong: "" });

  useEffect(() => {
    if (!ready) return;
    let alive = true;
    getRegionOptions(level, { sido, sigungu, gu }, membersOnly)
      .then((options) => alive && setDone({ key, items: options.items }))
      .catch(() => alive && setDone({ key, items: [] }));
    return () => {
      alive = false;
    };
  }, [level, key, ready, sido, sigungu, gu, membersOnly]);

  const fresh = ready && done.key === key;
  return { items: fresh ? done.items : [], loaded: fresh };
}

type List =
  | { state: "loading" }
  | { state: "error" }
  | { state: "ok"; items: ApiCommunity[]; total: number; hasNext: boolean };

type RegionPickerProps = {
  /** 고른 동네. 라디오 표시와 아래 선택 경로에 쓴다 */
  selected: ApiCommunity | null;
  onPick: (community: ApiCommunity) => void;
  title?: string;
  hint?: string;
  /**
   * 가입자가 있는 동네만 보여준다. 기본값이 true인 이유는 이 선택기를 쓰는 곳이
   * 관리자 화면이기 때문이다 — 통계도 공지 대상도 없는 동네를 고르게 하면 안 된다.
   * 가입 흐름에서 쓰려면 false로 둬야 첫 이웃이 될 동네를 고를 수 있다.
   */
  membersOnly?: boolean;
};

/**
 * 동네 선택 — 시·도 → 시·군·구 → (구가 있는 시만) 구 → 읍·면·동으로 좁히고 목록에서 고른다.
 * 관리자 대시보드·공지 관리 공용.
 *
 * <p>평면 목록은 동네가 늘면 쓸 수 없다. 전국 행정동이 등록되면 수천 곳이라, 이름을 미리
 * 알지 못하면 찾을 수 없고 목록 앞 20곳만 닿는다.
 *
 * <p>단계 옵션은 **등록된** 동네에서 모인다(E_073). 아직 아무도 인증하지 않은 행정동은
 * 나오지 않는다. 관리자 화면은 통계·공지 대상이 있는 동네만 골라야 하므로 이게 맞다.
 * 거기에 `membersOnly`로 가입자가 없는 동네까지 거른다 — 검색만 해도 동네가 등록되기 때문이다.
 */
export function RegionPicker({
  selected, onPick, title = "동네 선택", hint, membersOnly = true,
}: RegionPickerProps) {
  const [levels, setLevels] = useState<Levels>(EMPTY);
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [retry, setRetry] = useState(0);
  const [list, setList] = useState<{ key: string } & List>({ key: "", state: "loading" });
  // 고른 동네로 스크롤을 옮기고, 입력창에서 ↓로 목록에 들어가려면 실제 요소를 집어야 한다
  const listRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const sidoOptions = useRegionOptions("SIDO", levels, true, membersOnly);
  const sigunguOptions = useRegionOptions("SIGUNGU", levels, levels.sido !== "", membersOnly);
  const guOptions = useRegionOptions("GU", levels, levels.sigungu !== "", membersOnly);
  // 구가 있는 시에만 구 단계를 그린다. 없는 지역은 단계를 건너뛴다
  const showGu = guOptions.loaded && guOptions.items.length > 0;
  // 세종특별자치시는 시·군·구가 없어 시·도 바로 아래가 읍·면·동이다
  const sigunguMissing = sigunguOptions.loaded && sigunguOptions.items.length === 0;
  // 읍·면·동 옵션은 구가 있는 시면 구까지, 없으면 시·군·구까지 고른 뒤에 받는다.
  // 구가 있는지 알기 전에는 기다린다 — 먼저 열면 구를 고르는 순간 다시 닫힌다
  const dongReady = levels.sido !== "" && (sigunguMissing
    || (levels.sigungu !== "" && guOptions.loaded && (!showGu || levels.gu !== "")));
  const dongOptions = useRegionOptions("DONG", levels, dongReady, membersOnly);

  // 입력이 멈춘 뒤 검색 (300ms)
  useEffect(() => {
    const timer = setTimeout(() => setQuery(keyword.trim()), 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  const listKey = `${retry}:${scopeKey(levels)}:${query}`;
  // 목록은 시·군·구(구가 있으면 구)까지 고른 뒤에 띄운다. 조건이 읍·면·동 옵션과 같다.
  //
  // 시·도만 고르면 수백 곳이다(전국 3,500 행정동 중 서울·경기가 각각 800대). 한 화면에
  // 담을 수도, 50개 상한 안에 담을 수도 없다. 시·군·구까지 좁히면 평균 열몇 곳이 된다
  // (3,500 / 288). 페이지로 넘기지 않는 이유는 단계를 한 번 더 좁히는 게 더 빠르기 때문이다.
  const hasScope = dongReady;

  useEffect(() => {
    if (!hasScope) return;
    let alive = true;
    searchCommunities(query || undefined, 0, LIST_SIZE, toScope(levels), membersOnly)
      .then((page) => alive && setList({
        key: listKey, state: "ok",
        items: page.items, total: page.totalElements, hasNext: page.hasNext,
      }))
      .catch(() => alive && setList({ key: listKey, state: "error" }));
    return () => {
      alive = false;
    };
  }, [listKey, hasScope, levels, query, membersOnly]);

  const loading = hasScope && list.key !== listKey;

  /**
   * 고른 동네가 스크롤 밖에 있으면 보이는 자리로 끌어온다.
   *
   * 목록은 5줄쯤 보이고 그 아래로 스크롤된다. 다시 열었을 때 고른 동네가 아래에 숨어 있으면
   * 뭘 고른 상태인지 알 수 없다. 페이지가 같이 움직이지 않게 컨테이너의 scrollTop만 건드린다
   * ({@code scrollIntoView}는 바깥 스크롤까지 옮긴다).
   */
  useEffect(() => {
    const box = listRef.current;
    const picked = box?.querySelector<HTMLElement>('[aria-selected="true"]');
    if (!box || !picked) return;
    box.scrollTop = Math.max(0, picked.offsetTop - (box.clientHeight - picked.clientHeight) / 2);
  }, [list, selected?.id]);

  /** ↓↑로 후보 사이를 옮기고 Esc로 입력창에 돌아온다. 목록을 눈으로 훑지 않게 */
  const moveInList = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      e.preventDefault();
      searchRef.current?.focus();
      return;
    }
    if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
    const options = Array.from(
      listRef.current?.querySelectorAll<HTMLButtonElement>("button[role='option']") ?? [],
    );
    const at = options.indexOf(document.activeElement as HTMLButtonElement);
    if (at < 0) return;
    e.preventDefault();
    // 첫 항목에서 위로 가면 입력창으로 돌아간다
    if (e.key === "ArrowUp" && at === 0) searchRef.current?.focus();
    else options[e.key === "ArrowDown" ? Math.min(at + 1, options.length - 1) : at - 1]?.focus();
  };

  /** 한 단계를 고르면 아래 단계는 모두 비운다 */
  const pickLevel = (level: keyof Levels, value: string) => {
    setLevels((prev) => {
      if (level === "sido") return { ...EMPTY, sido: value };
      if (level === "sigungu") return { ...prev, sigungu: value, gu: "", dong: "" };
      if (level === "gu") return { ...prev, gu: value, dong: "" };
      return { ...prev, dong: value };
    });
  };

  const select = (level: keyof Levels, label: string, options: string[], locked: boolean, lockedBy: string) => (
    <label className={c("rg-f")}>
      <span className={c("t-caption rg-muted")}>{label}</span>
      <span className={c(`rg-selw${locked ? " rg-selw--off" : ""}`)}>
        <select
          className={c("rg-sel")}
          value={levels[level]}
          disabled={locked}
          aria-label={label}
          onChange={(e) => pickLevel(level, e.target.value)}
        >
          <option value="">{locked ? `${lockedBy} 먼저` : "전체"}</option>
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <span className={c("rg-caret")}>
          <Icon name="chevronD" size={13} />
        </span>
      </span>
    </label>
  );

  return (
    <div className={c("rg")}>
      <p className={c("t-label rg-title")}>{title}</p>

      <div className={c("rg-sels")}>
        {select("sido", "시·도", sidoOptions.items, false, "")}
        {select("sigungu", "시·군·구", sigunguOptions.items, levels.sido === "", "시·도")}
        {showGu ? select("gu", "구", guOptions.items, levels.sigungu === "", "시·군·구") : null}
        {select("dong", "읍·면·동", dongOptions.items, !dongReady, showGu ? "구" : "시·군·구")}
      </div>

      {/* 이름 검색은 목록 안에서 좁히는 입력이다. 목록이 없을 때 띄우면 눌러도 반응이 없다 */}
      {hasScope && (
        <input
          ref={searchRef}
          className={c("rg-q")}
          type="search"
          placeholder="동네 이름 검색"
          aria-label="동네 이름 검색"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => {
            // ↓로 목록에 들어간다. 한글 조합 중에는 건드리지 않는다
            if (e.key !== "ArrowDown" || e.nativeEvent.isComposing) return;
            const first = listRef.current?.querySelector<HTMLButtonElement>("button[role='option']");
            if (!first) return;
            e.preventDefault();
            first.focus();
          }}
        />
      )}

      {!hasScope && (
        <p className={c("t-caption rg-muted rg-note")}>{nextStepLabel(levels, showGu)}</p>
      )}

      {hasScope && (
        <>
          <div className={c("t-caption rg-muted rg-head")}>
            <span>{list.state === "ok" && !loading ? `동네 ${list.total.toLocaleString()}곳` : "동네"}</span>
          </div>

          <div
            className={c("rg-list")}
            role="listbox"
            aria-label={title}
            aria-busy={loading}
            ref={listRef}
            onKeyDown={moveInList}
          >
            {loading && <p className={c("t-caption rg-muted rg-note")}>불러오는 중…</p>}

            {!loading && list.state === "error" && (
              <div className={c("rg-note")} role="alert">
                <p className={c("t-caption rg-muted rg-note-text")}>동네를 불러오지 못했어요.</p>
                <Button variant="secondary" size="sm" icon="refresh" onClick={() => setRetry((n) => n + 1)}>
                  다시 시도
                </Button>
              </div>
            )}

            {!loading && list.state === "ok" && list.items.length === 0 && (
              <p className={c("t-caption rg-muted rg-note")}>조건에 맞는 동네가 없어요.</p>
            )}

            {!loading && list.state === "ok" && list.items.map((community) => {
              const on = community.id === selected?.id;
              return (
                <button
                  key={community.id}
                  type="button"
                  role="option"
                  aria-selected={on}
                  className={c(`rg-item${on ? " rg-item--on" : ""}`)}
                  onClick={() => onPick(community)}
                >
                  <span className={c(`rg-radio${on ? " rg-radio--on" : ""}`)} />
                  <span className={c("rg-txt")}>
                    <span className={c("rg-name")}>{community.name}</span>
                    <span className={c("rg-addr")}>{community.displayAddress}</span>
                  </span>
                </button>
              );
            })}
          </div>

          {!loading && list.state === "ok" && list.hasNext && (
            <p className={c("t-caption rg-muted rg-note")}>
              {list.total.toLocaleString()}곳 중 {list.items.length}곳만 보여줘요. 읍·면·동을 고르거나 이름으로 찾아 주세요.
            </p>
          )}
        </>
      )}

      {selected && (
        <p className={c("t-caption rg-crumb")}>
          선택: {[...selected.displayAddress.split(" "), selected.name].join(" › ")}
        </p>
      )}
      {hint && <p className={c("t-caption rg-muted rg-note")}>{hint}</p>}
    </div>
  );
}
