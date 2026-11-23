import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { SearchField } from "@/components/ui/search-field";
import { Alert } from "@/components/ui/alert";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ApiError } from "@/api/client";
import { type ApiCommunity, findNearbyCommunities, searchCommunities } from "@/api/communities";
import { GeoError, getCurrentCoords } from "@/utils/geolocation";
import styles from "@/components/custom/community/CommunityFinder.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** key는 이 결과가 어느 검색어의 것인지. 현재 검색어와 다르면 로딩이다 */
type Result = { key: string } & (
  | { state: "error"; message: string }
  | { state: "ok"; items: ApiCommunity[]; truncated: boolean; total: number }
);

/** 위치로 찾은 결과의 key. 어떤 검색어 key와도 겹치지 않는다 */
const NEARBY_KEY = "nearby";

/** 한 글자로는 검색하지 않는다. 후보가 너무 넓고 서버에서도 행정동 보충을 하지 않는다 */
const MIN_KEYWORD = 2;

/** 목록 문구: "표시 주소 · 이웃 N명" */
function describe(community: ApiCommunity): string {
  return `${community.displayAddress} · 이웃 ${community.verifiedMemberCount}명`;
}

type CommunityFinderProps = {
  /** 후보를 눌렀을 때 갈 주소 — 가입·동네 변경 흐름별 인증 화면 */
  linkTo: (community: ApiCommunity) => string;
  /** 지금 소속 동네. 후보에 '지금 동네' 표시 */
  currentId?: string;
};

/** 동네 찾기 — 이름 검색(E_009) · 현재 위치 주변(E_071) 후보 목록. 가입과 동네 변경 공용 */
export function CommunityFinder({ linkTo, currentId }: CommunityFinderProps) {
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  // 위치로 찾은 결과는 검색어와 무관하다. 이름 검색이 덮어쓰지 않게 출처를 구분한다
  const [source, setSource] = useState<"search" | "nearby">("search");
  const [result, setResult] = useState<Result | null>(null);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  // ↑↓로 후보 사이를 옮기려면 실제 링크를 집어야 한다
  const listRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  // 입력이 멈춘 뒤 검색 (300ms). 일부만 입력해도 후보가 좁혀진다
  useEffect(() => {
    const t = setTimeout(() => setQuery(keyword.trim()), 300);
    return () => clearTimeout(t);
  }, [keyword]);

  // 로딩은 상태로 두지 않고 "결과가 현재 검색어의 것인지"로 가린다.
  // 효과 안에서 setState하면 입력마다 렌더가 한 번 더 돈다
  const key = `${retry}:${query}`;
  // 짧은 검색어는 요청하지 않으므로 로딩도 아니다.
  // 빈 검색어도 마찬가지다 — 전국 동네 중 앞 20곳을 나열하면 고를 근거가 없다
  const waiting = source === "search" && query.length < MIN_KEYWORD;
  const loading = source === "search" && !waiting && result?.key !== key;

  // 이름 검색 (E_009). 위치 없이 검색할 수 있고, 아직 등록되지 않은 행정동은 검색이 등록한다
  useEffect(() => {
    if (source !== "search" || waiting) return;
    let alive = true;
    searchCommunities(query)
      .then((page) => {
        if (alive) {
          setResult({
            key, state: "ok", items: page.items,
            truncated: page.hasNext, total: page.totalElements,
          });
        }
      })
      .catch((e: unknown) => {
        if (!alive) return;
        const message = e instanceof ApiError ? e.message : "동네를 불러오지 못했어요.";
        setResult({ key, state: "error", message });
      });
    return () => {
      alive = false;
    };
  }, [key, query, source, waiting]);

  /** 현재 위치로 주변 동네 찾기 (E_071). 이 조회 자체는 인증이 아니다 */
  const onLocate = async () => {
    setLocating(true);
    setLocateError(null);
    try {
      const coords = await getCurrentCoords();
      const nearby = await findNearbyCommunities(coords);
      setSource("nearby");
      setKeyword("");
      setQuery("");
      setResult({
        key: NEARBY_KEY, state: "ok", items: nearby.items,
        truncated: nearby.truncated, total: nearby.items.length,
      });
    } catch (e: unknown) {
      if (e instanceof GeoError) setLocateError(e.message);
      else if (e instanceof ApiError) setLocateError(e.message);
      else setLocateError("주변 동네를 찾지 못했어요.");
    } finally {
      setLocating(false);
    }
  };

  return (
    <>
      <SearchField
        placeholder="동네·단지 이름으로 검색"
        label="동네 검색"
        inputRef={searchRef}
        onKeyDown={(e) => {
          // 아래 화살표로 후보 목록에 들어간다. 한글 조합 중에는 건드리지 않는다
          if (e.key !== "ArrowDown" || e.nativeEvent.isComposing) return;
          const first = listRef.current?.querySelector<HTMLAnchorElement>("a");
          if (!first) return;
          e.preventDefault();
          first.focus();
        }}
        value={keyword}
        onChange={(value) => {
          setSource("search");
          setKeyword(value);
        }}
        onSubmit={() => {
          // 디바운스를 기다리지 않고 지금 검색한다
          setSource("search");
          setQuery(keyword.trim());
        }}
      />

      <button type="button" className={c("finder-locate t-label")} onClick={onLocate} disabled={locating}>
        <Icon name="pin" size={18} />
        {locating ? "위치 확인 중…" : "현재 위치로 우리 동네 찾기"}
      </button>

      {locateError && (
        <Alert tone="warning" title="위치로 찾지 못했어요">
          {locateError}
        </Alert>
      )}

      {!waiting && <p className={c("t-label finder-results-label")}>검색 결과</p>}

      {loading && (
        <Loading label="동네를 불러오는 중">
          <div className={c("finder-card")}>
            <SkeletonItem layout="row" />
            <SkeletonItem layout="row" />
          </div>
        </Loading>
      )}

      {!loading && !waiting && result?.state === "error" && (
        <ErrorState onRetry={() => setRetry((n) => n + 1)}>
          {result.message}
        </ErrorState>
      )}

      {!loading && !waiting && result?.state === "ok" && result.items.length === 0 && (
        <EmptyState icon="search" title="찾는 동네가 없어요">
          이름을 다시 확인하거나 현재 위치로 찾아 주세요.
        </EmptyState>
      )}

      {!loading && !waiting && result?.state === "ok" && result.items.length > 0 && (
        <div
          className={c("finder-card")}
          ref={listRef}
          onKeyDown={(e) => {
            if (e.key !== "ArrowDown" && e.key !== "ArrowUp" && e.key !== "Escape") return;
            if (e.key === "Escape") {
              e.preventDefault();
              searchRef.current?.focus();
              return;
            }
            const links = Array.from(
              listRef.current?.querySelectorAll<HTMLAnchorElement>("a") ?? [],
            );
            const at = links.indexOf(document.activeElement as HTMLAnchorElement);
            if (at < 0) return;
            e.preventDefault();
            // 첫 항목에서 위로 가면 입력창으로 돌아간다
            if (e.key === "ArrowUp" && at === 0) searchRef.current?.focus();
            else links[e.key === "ArrowDown" ? Math.min(at + 1, links.length - 1) : at - 1]?.focus();
          }}
        >
          {result.items.map((community) => (
            <Link key={community.id} to={linkTo(community)} className={c("finder-candidate")}>
              <span className={c("finder-candidate-icon")}>
                <Icon name="pin" size={20} />
              </span>
              <span className={c("finder-candidate-body")}>
                <span className={c("t-body finder-candidate-name")}>{community.name}</span>
                <span className={c("t-caption finder-muted")}>
                  {community.id === currentId ? `지금 동네 · ${describe(community)}` : describe(community)}
                </span>
              </span>
              <Icon name="chevronR" size={18} />
            </Link>
          ))}
        </div>
      )}

      {waiting && (
        <p className={c("t-caption finder-muted")}>
          {query.length === 0
            ? "동네나 단지 이름을 입력하거나, 현재 위치로 찾아 주세요."
            : "두 글자 이상 입력해 주세요."}
        </p>
      )}

      {!loading && !waiting && result?.state === "ok" && result.truncated && (
        <p className={c("t-caption finder-muted")}>
          {source === "nearby"
            ? "가까운 동네 20개만 보여줘요. 찾는 동네가 없으면 이름으로 검색해 주세요."
            : `총 ${result.total}곳 중 ${result.items.length}곳만 보여줘요. 동 이름까지 입력하면 좁혀져요.`}
        </p>
      )}
    </>
  );
}
