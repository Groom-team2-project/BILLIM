import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout } from "@/stores/session";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Avatar } from "@/components/ui/avatar";
import { useUi } from "@/components/overlay/UiContext";
import { Photo } from "@/components/custom/items/ItemCard";
import { TrustLines } from "@/components/custom/profile/OwnerCard";
import styles from "@/pages/profile/ProfilePage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";
import { ApiError } from "@/api/client";
import { type ApiItemSummary, changeItemVisibility, deleteItem, getMyItems } from "@/api/items";
import { UNKNOWN_PLACE, toCategoryId } from "@/utils/itemView";
import { objectParticle } from "@/utils/korean";

const c = classes(styles);

/** 프로토타입 목데이터 — 내 계정. API 연동 시 교체 */
const MY_TRUST = { kept: 8, total: 8, done: 12, lent: 8, since: "2026년 4월" };

export function ProfilePage() {
  const { confirm, toast } = useUi();
  const navigate = useNavigate();
  // 등록한 물건 — B_024 실데이터. null이면 불러오는 중
  const [myItems, setMyItems] = useState<ApiItemSummary[] | null>(null);
  const [loadError, setLoadError] = useState(false);

  const loadMyItems = useCallback(() => {
    getMyItems()
      .then((page) => {
        setLoadError(false);
        setMyItems(page.items);
      })
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 401) {
          navigate("/login");
          return;
        }
        setLoadError(true);
      });
  }, [navigate]);

  useEffect(() => {
    loadMyItems();
  }, [loadMyItems]);

  // 서버 거절 사유를 안내하고, 버전 충돌이면 최신 목록으로 다시 맞춘다
  const handleCommandError = (e: unknown) => {
    if (e instanceof ApiError && e.status === 401) {
      navigate("/login");
      return;
    }
    if (e instanceof ApiError && e.code === "VERSION_CONFLICT") {
      toast("다른 곳에서 바뀐 내용이 있어 목록을 새로 불러왔어요");
      loadMyItems();
      return;
    }
    toast(e instanceof ApiError ? e.message : "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요");
  };

  /* 논리 삭제 (B_023) — 진행 중 요청이 있으면 막음 (정책 및 상태.md DELETED) */
  const removeItem = (it: ApiItemSummary) => {
    const requests = it.pendingRequestCount ?? 0;
    if (requests > 0) {
      toast(`진행 중인 요청 ${requests}건을 먼저 정리해 주세요`);
      return;
    }
    confirm({
      title: `${it.title}${objectParticle(it.title)} 삭제할까요?`,
      body: "삭제하면 목록에서 사라져요. 지난 거래 기록은 그대로 남아요.",
      confirm: "삭제",
      danger: true,
      onConfirm: () => {
        deleteItem(it.id, it.version)
          .then(() => {
            setMyItems((prev) => prev?.filter((x) => x.id !== it.id) ?? prev);
            toast("삭제했어요");
          })
          .catch(handleCommandError);
      },
    });
  };

  /* 공개 중지·다시 공개 (B_022). 공개 중지는 검색·신규 요청을 막으므로 확인 후 전환 */
  const changeVisibility = (it: ApiItemSummary, next: "PUBLIC" | "HIDDEN") =>
    changeItemVisibility(it.id, next, it.version)
      .then((detail) => {
        setMyItems(
          (prev) =>
            prev?.map((x) => (x.id === it.id ? { ...x, visibility: detail.visibility, version: detail.version } : x)) ??
            prev,
        );
        toast(next === "HIDDEN" ? "공개를 중지했어요" : "다시 공개했어요");
      })
      .catch(handleCommandError);

  const toggleVisibility = (it: ApiItemSummary) => {
    if (it.visibility === "PUBLIC") {
      confirm({
        title: `${it.title}${objectParticle(it.title)} 공개 중지할까요?`,
        body: "검색과 새 요청이 멈춰요. 진행 중인 대여는 그대로 유지돼요.",
        confirm: "공개 중지",
        onConfirm: () => {
          void changeVisibility(it, "HIDDEN");
        },
      });
    } else {
      void changeVisibility(it, "PUBLIC");
    }
  };
  return (
    <div className={c("profile page-window")}>
      <PageHeader title="내 프로필" />

      <div className={c("profile-me")}>
        <Avatar name="나" size={56} />
        <div>
          <div className={c("t-title-lg")}>나</div>
          <div className={c("t-caption profile-muted")}>새솔마을 5단지 · 2026년 4월 가입 · 동네 인증됨</div>
        </div>
      </div>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>신뢰 지표</p>
        <div className={c("profile-card")}>
          <TrustLines trust={MY_TRUST} />
        </div>
        <p className={c("t-caption profile-muted profile-note")}>점수나 별점 대신, 실제 거래 기록을 그대로 보여줘요.</p>
      </section>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>등록한 물건</p>
        <div className={c("profile-card profile-card--list")}>
          {loadError ? (
            <p className={c("t-caption profile-muted profile-empty")}>
              등록한 물건을 불러오지 못했어요.{" "}
              <button
                type="button"
                className={c("profile-retry")}
                onClick={() => {
                  setLoadError(false);
                  setMyItems(null);
                  loadMyItems();
                }}
              >
                다시 시도
              </button>
            </p>
          ) : myItems === null ? (
            <p className={c("t-caption profile-muted profile-empty")}>불러오는 중…</p>
          ) : myItems.length === 0 ? (
            <p className={c("t-caption profile-muted profile-empty")}>등록한 물건이 없어요.</p>
          ) : (
            myItems.map((it) => (
              <button
                key={it.id}
                type="button"
                className={c("profile-row")}
                onClick={() => navigate(`/items/${it.id}`)}
              >
                <Photo
                  cat={toCategoryId(it.category.code)}
                  src={it.thumbnailUrl}
                  iconSize={18}
                  className={c("profile-row-thumb")}
                />
                <span className={c("profile-row-body")}>
                  <span className={c("profile-row-title")}>{it.title}</span>
                  <span className={c("t-caption profile-muted")}>
                    {it.place.name ?? UNKNOWN_PLACE}
                    {(it.pendingRequestCount ?? 0) > 0 ? ` · 요청 ${it.pendingRequestCount}건` : ""}
                  </span>
                </span>
                <span
                  role="button"
                  tabIndex={0}
                  className={c(`profile-pill${it.visibility === "HIDDEN" ? " profile-pill--off" : ""}`)}
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleVisibility(it);
                  }}
                >
                  {it.visibility === "PUBLIC" ? "공개 중" : "공개 중지"}
                </span>
                <span
                  role="button"
                  tabIndex={0}
                  className={c("profile-row-delete")}
                  aria-label={`${it.title} 삭제`}
                  onClick={(e) => {
                    e.stopPropagation();
                    removeItem(it);
                  }}
                >
                  <Icon name="x" size={16} />
                </span>
                <Icon name="chevronR" size={16} />
              </button>
            ))
          )}
        </div>
      </section>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>관리</p>
        <div className={c("profile-card profile-card--list")}>
          <button type="button" className={c("profile-row")} onClick={() => navigate("/blocked")}>
            <Icon name="shield" size={20} />
            <span className={c("profile-row-body")}>
              <span className={c("profile-row-title")}>차단한 이웃</span>
            </span>
            <span className={c("t-caption profile-muted")}>1명</span>
            <Icon name="chevronR" size={16} />
          </button>
          <button type="button" className={c("profile-row")} onClick={() => navigate("/reports")}>
            <Icon name="flag" size={20} />
            <span className={c("profile-row-body")}>
              <span className={c("profile-row-title")}>신고 내역</span>
            </span>
            <span className={c("t-caption profile-muted")}>1건</span>
            <Icon name="chevronR" size={16} />
          </button>
        </div>
      </section>

      <Button
        variant="secondary"
        block
        onClick={() =>
          confirm({
            title: "로그아웃할까요?",
            body: "다시 로그인하면 이어서 이용할 수 있어요.",
            confirm: "로그아웃",
            onConfirm: () => {
              logout();
              toast("로그아웃되었어요");
              navigate("/login");
            },
          })
        }
      >
        로그아웃
      </Button>
    </div>
  );
}
