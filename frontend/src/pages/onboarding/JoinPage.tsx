import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { PageHeader } from "@/components/layout/header";
import { SearchField } from "@/components/ui/search-field";
import { Alert } from "@/components/ui/alert";
import { EmptyState, ErrorState, Loading, SkeletonItem } from "@/components/ui/state";
import { ApiError } from "@/api/client";
import { type ApiCommunity, findNearbyCommunities, searchCommunities } from "@/api/communities";
import { GeoError, getCurrentCoords } from "@/utils/geolocation";
import styles from "@/pages/onboarding/JoinPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** key는 이 결과가 어느 검색어의 것인지. 현재 검색어와 다르면 로딩이다 */
type Result = { key: string } & (
  | { state: "error"; message: string }
  | { state: "ok"; items: ApiCommunity[]; truncated: boolean }
);

/** 위치로 찾은 결과의 key. 어떤 검색어 key와도 겹치지 않는다 */
const NEARBY_KEY = "nearby";

/** 목록 문구: "표시 주소 · 이웃 N명" */
function describe(community: ApiCommunity): string {
  return `${community.displayAddress} · 이웃 ${community.verifiedMemberCount}명`;
}

export function JoinPage() {
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  // 위치로 찾은 결과는 검색어와 무관하다. 이름 검색이 덮어쓰지 않게 출처를 구분한다
  const [source, setSource] = useState<"search" | "nearby">("search");
  const [result, setResult] = useState<Result | null>(null);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  // 입력이 멈춘 뒤 검색 (300ms). 일부만 입력해도 후보가 좁혀진다
  useEffect(() => {
    const t = setTimeout(() => setQuery(keyword.trim()), 300);
    return () => clearTimeout(t);
  }, [keyword]);

  // 로딩은 상태로 두지 않고 "결과가 현재 검색어의 것인지"로 가린다.
  // 효과 안에서 setState하면 입력마다 렌더가 한 번 더 돈다
  const key = `${retry}:${query}`;
  const loading = source === "search" && result?.key !== key;

  // 이름 검색 — 처음에는 전체 활성 동네를 보여준다 (위치 없이 검색 가능, E_009)
  useEffect(() => {
    if (source !== "search") return;
    let alive = true;
    searchCommunities(query || undefined)
      .then((page) => {
        if (alive) setResult({ key, state: "ok", items: page.items, truncated: false });
      })
      .catch((e: unknown) => {
        if (!alive) return;
        const message = e instanceof ApiError ? e.message : "동네를 불러오지 못했어요.";
        setResult({ key, state: "error", message });
      });
    return () => {
      alive = false;
    };
  }, [key, query, source]);

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
      setResult({ key: NEARBY_KEY, state: "ok", items: nearby.items, truncated: nearby.truncated });
    } catch (e: unknown) {
      if (e instanceof GeoError) setLocateError(e.message);
      else if (e instanceof ApiError) setLocateError(e.message);
      else setLocateError("주변 동네를 찾지 못했어요.");
    } finally {
      setLocating(false);
    }
  };

  return (
    <main className={c("join")}>
      <PageHeader title="우리 동네 찾기" backTo="/login" standalone />
      <div className={c("join-body")}>
        <div>
          <h1 className={c("t-title-lg join-title")}>어느 동네에 사세요?</h1>
          <p className={c("t-body join-muted join-desc")}>
            실제 사는 동네(단지)에서만 이웃과 물건을 주고받아요. 먼저 우리 동네를 찾아 주세요.
          </p>
        </div>

        <SearchField
          placeholder="동네·단지 이름으로 검색"
          label="동네 검색"
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

        <button type="button" className={c("join-locate t-label")} onClick={onLocate} disabled={locating}>
          <Icon name="pin" size={18} />
          {locating ? "위치 확인 중…" : "현재 위치로 우리 동네 찾기"}
        </button>

        {locateError && (
          <Alert tone="warning" title="위치로 찾지 못했어요">
            {locateError}
          </Alert>
        )}

        <p className={c("t-label join-results-label")}>검색 결과</p>

        {loading && (
          <Loading label="동네를 불러오는 중">
            <div className={c("join-card")}>
              <SkeletonItem layout="row" />
              <SkeletonItem layout="row" />
            </div>
          </Loading>
        )}

        {!loading && result?.state === "error" && (
          <ErrorState onRetry={() => setRetry((n) => n + 1)}>
            {result.message}
          </ErrorState>
        )}

        {!loading && result?.state === "ok" && result.items.length === 0 && (
          <EmptyState icon="search" title="찾는 동네가 없어요">
            이름을 다시 확인하거나 현재 위치로 찾아 주세요.
          </EmptyState>
        )}

        {!loading && result?.state === "ok" && result.items.length > 0 && (
          <div className={c("join-card")}>
            {result.items.map((community) => (
              <Link
                key={community.id}
                to={`/verify?communityId=${community.id}`}
                className={c("join-candidate")}
              >
                <span className={c("join-candidate-icon")}>
                  <Icon name="pin" size={20} />
                </span>
                <span className={c("join-candidate-body")}>
                  <span className={c("t-body join-candidate-name")}>{community.name}</span>
                  <span className={c("t-caption join-muted")}>{describe(community)}</span>
                </span>
                <Icon name="chevronR" size={18} />
              </Link>
            ))}
          </div>
        )}

        {!loading && result?.state === "ok" && result.truncated && (
          <p className={c("t-caption join-muted")}>
            가까운 동네 20개만 보여줘요. 찾는 동네가 없으면 이름으로 검색해 주세요.
          </p>
        )}

        <Alert tone="info" title="동네는 실제 사는 곳으로">
          다음 단계에서 위치로 한 번 확인해요. 정확한 위치나 집 주소는 저장하지 않아요.
        </Alert>
      </div>
    </main>
  );
}
