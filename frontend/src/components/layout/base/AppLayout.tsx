import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useSession } from "@/stores/session";
import { useUi } from "@/components/overlay/UiContext";
import { type MoreMenuItem } from "@/components/ui/more-menu";
import { CHATS } from "@/data/chat";
import { Footer } from "@/components/layout/footer";
import { Header } from "@/components/layout/header";
import { TabBar } from "@/components/layout/nav";
import styles from "@/components/layout/base/AppLayout.module.css";
import { classes } from "@/utils/classes";
import { ApiError } from "@/api/client";
import { changeItemVisibility } from "@/api/items";
import { setCurrentItem, useCurrentItem } from "@/stores/currentItem";
import { objectParticle } from "@/utils/korean";

const c = classes(styles);

/** 모바일 헤더 제목 — 홈은 워드마크, 탭 화면은 왼쪽 제목 (§7) */
const PAGE_TITLES: Record<string, string> = {
  "/search": "물건 찾기",
  "/chat": "채팅",
  "/rentals": "내 대여",
};

/** 화면이 자체 PageHeader(.hdr)를 그리는 경로 — 앱 헤더는 제목·뒤로를 표시하지 않음 */
const OWN_HEADER = [
  /^\/profile$/,
  /^\/blocked$/,
  /^\/reports$/,
  /^\/register$/,
  /^\/notifications$/,
  /^\/neighborhoods$/,
  /^\/neighbors\//,
  /^\/policy\//,
  /^\/items\/[^/]+\/request$/,
];

/** 앱 공통 셸 — 헤더·본문·푸터·하단 탭 조립 (DESIGN_SYSTEM.md §7) */
export function AppLayout() {
  const { loggedIn } = useSession();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { confirm, report, toast } = useUi();
  const chatRoomMatch = pathname.match(/^\/chat\/(.+)$/);
  const chatName = chatRoomMatch ? CHATS.find((ch) => ch.id === chatRoomMatch[1])?.name : undefined;
  const hasOwnHeader = OWN_HEADER.some((re) => re.test(pathname));
  const title = chatName ?? PAGE_TITLES[pathname];
  /* 하위 화면(§7): 모바일 헤더는 뒤로+제목+더보기, 하단 탭 없음 */
  const isSubPage = /^\/items\/[^/]+$/.test(pathname) || !!chatRoomMatch || hasOwnHeader;
  /* 앱 헤더가 뒤로·제목을 그리는 화면 (자체 헤더가 없는 하위 화면) */
  const headerSub = isSubPage && !hasOwnHeader;

  /* 하위 화면 더보기(⋮) — 맥락별 메뉴 */
  const itemMatch = pathname.match(/^\/items\/([^/]+)$/);
  // 물건 상세의 더보기 — 상세 화면이 불러온 실제 물건(B_020)을 쓴다. 불러오기 전에는 메뉴 없음
  const moreItem = useCurrentItem(itemMatch?.[1] ?? "");
  const doReport = (name: string) => report({ name, onConfirm: () => toast("신고를 접수했어요") });
  const changeVisibility = async (next: "PUBLIC" | "HIDDEN") => {
    if (!moreItem) return;
    try {
      setCurrentItem(await changeItemVisibility(moreItem.id, next, moreItem.version));
      toast(next === "HIDDEN" ? "공개를 중지했어요" : "다시 공개했어요");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "처리하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };
  const moreItems: MoreMenuItem[] = moreItem
    ? moreItem.allowedActions.includes("EDIT")
      ? /* 내 물건 — 신고·소유자 프로필 대신 관리 항목 */
        [
          { label: "수정", onSelect: () => navigate(`/register?itemId=${moreItem.id}`) },
          { label: "요청 보기", onSelect: () => navigate("/rentals") },
          {
            label: moreItem.visibility === "PUBLIC" ? "공개 중지" : "다시 공개",
            onSelect: () => {
              if (moreItem.visibility === "PUBLIC") {
                confirm({
                  title: `${moreItem.title}${objectParticle(moreItem.title)} 공개 중지할까요?`,
                  body: "검색과 새 요청이 멈춰요. 진행 중인 대여는 그대로 유지돼요.",
                  confirm: "공개 중지",
                  onConfirm: () => void changeVisibility("HIDDEN"),
                });
              } else {
                void changeVisibility("PUBLIC");
              }
            },
          },
        ]
      : [
          { label: "소유자 프로필", onSelect: () => navigate(`/neighbors/${moreItem.owner.displayName}`) },
          {
            label: `${moreItem.owner.displayName} 님 신고하기`,
            danger: true,
            onSelect: () => doReport(moreItem.owner.displayName),
          },
        ]
    : chatRoomMatch && chatName
      ? [
          { label: "이웃 프로필", onSelect: () => navigate(`/neighbors/${chatName}`) },
          { label: "신고하기", danger: true, onSelect: () => doReport(chatName) },
          {
            label: "차단",
            danger: true,
            onSelect: () =>
              confirm({
                title: `${chatName} 님을 차단할까요?`,
                body: "차단하면 서로 대화할 수 없고 물건도 볼 수 없어요.",
                confirm: "차단",
                danger: true,
                onConfirm: () => {
                  toast("차단했어요");
                  navigate("/");
                },
              }),
          },
        ]
      : [];

  if (!loggedIn) return <Navigate to="/login" replace />;

  return (
    <div className={c("shell")}>
      <Header
        sub={headerSub}
        title={headerSub ? title : undefined}
        searchHidden={pathname === "/"}
        moreItems={moreItems}
        onBack={() => navigate(-1)}
      />

      <main className={c(`shell-main${hasOwnHeader ? " shell-main--window" : isSubPage ? " shell-main--sub" : ""}`)}>
        <Outlet />
      </main>

      {pathname.startsWith("/chat") ? null : <Footer />}
      {isSubPage ? null : <TabBar />}
    </div>
  );
}
