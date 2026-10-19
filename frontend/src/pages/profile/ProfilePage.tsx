import { useEffect, useState } from "react";
import { getMyProfile, type ApiMyProfile } from "@/api/members";
import { ApiError } from "@/api/client";
import { ErrorState, Loading, Skeleton } from "@/components/ui/state";
import { formatYearMonth } from "@/utils/itemView";
import { useNavigate } from "react-router-dom";
import { logout } from "@/stores/session";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Avatar } from "@/components/ui/avatar";
import { useUi } from "@/components/overlay/UiContext";
import { Photo } from "@/components/custom/items/ItemCard";
import { LiveTrustLines } from "@/components/custom/profile/LiveTrustLines";
import styles from "@/pages/profile/ProfilePage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";
import { deleteItem, setVisibility, useMyItems } from "@/stores/items";
import { objectParticle } from "@/utils/korean";

const c = classes(styles);

export function ProfilePage() {
  const { confirm, toast } = useUi();
  const navigate = useNavigate();
  const myItems = useMyItems();
  const [profile, setProfile] = useState<ApiMyProfile | null>(null);
  const [profileError, setProfileError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let alive = true;
    getMyProfile().then((value) => { if (alive) setProfile(value); }).catch((e: unknown) => {
      if (!alive) return;
      if (e instanceof ApiError && e.unauthenticated) { navigate("/login"); return; }
      setProfileError(e instanceof ApiError ? e.message : "프로필을 불러오지 못했어요.");
    });
    return () => { alive = false; };
  }, [retry, navigate]);

  /* 논리 삭제 — 진행 중 요청이 있으면 막음 (정책 및 상태.md DELETED) */
  const removeItem = (id: number, title: string, requests: number) => {
    if (requests > 0) {
      toast(`진행 중인 요청 ${requests}건을 먼저 정리해 주세요`);
      return;
    }
    confirm({
      title: `${title}${objectParticle(title)} 삭제할까요?`,
      body: "삭제하면 목록에서 사라져요. 지난 거래 기록은 그대로 남아요.",
      confirm: "삭제",
      danger: true,
      onConfirm: () => {
        deleteItem(id);
        toast("삭제했어요");
      },
    });
  };

  /* 공개 중지는 검색·신규 요청을 막으므로 확인 후 전환 (정책 및 상태.md) */
  const toggleVisibility = (id: number, title: string, current: "PUBLIC" | "HIDDEN") => {
    if (current === "PUBLIC") {
      confirm({
        title: `${title}${objectParticle(title)} 공개 중지할까요?`,
        body: "검색과 새 요청이 멈춰요. 진행 중인 대여는 그대로 유지돼요.",
        confirm: "공개 중지",
        onConfirm: () => {
          setVisibility(id, "HIDDEN");
          toast("공개를 중지했어요");
        },
      });
    } else {
      setVisibility(id, "PUBLIC");
      toast("다시 공개했어요");
    }
  };
  if (profileError) return <ErrorState title="프로필을 불러오지 못했어요" onRetry={() => { setProfileError(""); setRetry((n) => n + 1); }}>{profileError}</ErrorState>;
  if (!profile) return <Loading label="프로필 불러오는 중"><Skeleton /></Loading>;
  return (
    <div className={c("profile page-window")}>
      <PageHeader title="내 프로필" />

      <div className={c("profile-me")}>
        <Avatar name={profile.member.displayName} size={56} />
        <div>
          <div className={c("t-title-lg")}>{profile.member.displayName}</div>
          <div className={c("t-caption profile-muted")}>{formatYearMonth(profile.member.joinedAt)} 가입</div>
        </div>
      </div>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>신뢰 지표</p>
        <div className={c("profile-card")}>
          <LiveTrustLines memberId={profile.member.id} />
        </div>
        <p className={c("t-caption profile-muted profile-note")}>점수나 별점 대신, 실제 거래 기록을 그대로 보여줘요.</p>
      </section>

      <section className={c("profile-section")}>
        <p className={c("t-label profile-muted profile-section-label")}>등록한 물건</p>
        <div className={c("profile-card profile-card--list")}>
          {myItems.length === 0 ? (
            <p className={c("t-caption profile-muted profile-empty")}>등록한 물건이 없어요.</p>
          ) : (
            myItems.map((it) => (
              <button
                key={it.id}
                type="button"
                className={c("profile-row")}
                onClick={() => navigate(`/items/${it.id}?state=own`)}
              >
                <Photo cat={it.cat} iconSize={18} className={c("profile-row-thumb")} />
                <span className={c("profile-row-body")}>
                  <span className={c("profile-row-title")}>{it.title}</span>
                  <span className={c("t-caption profile-muted")}>
                    {it.place}
                    {it.requests > 0 ? ` · 요청 ${it.requests}건` : ""}
                  </span>
                </span>
                <span
                  role="button"
                  tabIndex={0}
                  className={c(`profile-pill${it.visibility === "HIDDEN" ? " profile-pill--off" : ""}`)}
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleVisibility(it.id, it.title, it.visibility);
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
                    removeItem(it.id, it.title, it.requests);
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
