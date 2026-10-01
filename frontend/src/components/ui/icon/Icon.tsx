/** 24x24 스트로크 아이콘 — 프로토타입 아이콘 패스 원본 (이름 체계 동일) */
const PATHS: Record<string, string> = {
  baby: "M9 4h6M9.6 4l-.4 2.4h5.6L14.4 4M8.5 6.4h7l-.5 12.2a2 2 0 0 1-2 1.9h-2a2 2 0 0 1-2-1.9zM10.3 11h3.4M10.3 14h3.4",
  music: "M7 18a2.5 2.5 0 1 0 5 0 2.5 2.5 0 1 0-5 0M12 18V6l7-2v10M14 16a2.5 2.5 0 1 0 5 0 2.5 2.5 0 1 0-5 0",
  sports: "M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10",
  kitchen: "M8 3v5M11 3v5M8 8h3M9.5 8v13M16.2 3c2.6 2.3 2.6 8 0 10.3M16.2 13.3V21",
  home: "M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z",
  search: "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4",
  plus: "M12 5v14M5 12h14",
  swap: "M9 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM15 7a5 5 0 1 1 0 10",
  bell: "M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0",
  user: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20a7.5 7.5 0 0 1 15 0",
  calendar: "M5 6h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zM4 10h16M8 4v4M16 4v4",
  pin: "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
  chevronL: "M15 5l-7 7 7 7",
  chevronR: "M9 5l7 7-7 7",
  chevronD: "M6 9l6 6 6-6",
  filter: "M4 6h16M7 12h10M10 18h4",
  map: "M9 5 3.5 7v12L9 17l6 2 5.5-2V5L15 7zM9 5v12M15 7v12",
  list: "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01",
  check: "M5 12.5l4.5 4.5L19 7.5",
  x: "M6 6l12 12M18 6 6 18",
  alert: "M12 4 2.8 19.5h18.4zM12 10v4.5M12 17.2v.01",
  info: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 11v5.5M12 7.8v.01",
  clock: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2",
  ring: "M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12z",
  dash: "M7 12h10",
  returned: "M4 12.5l4 4 8-9M12 16.5l1 1 7.5-9",
  shield: "M12 3.5 5 6v5.5c0 4.3 3 7.7 7 9 4-1.3 7-4.7 7-9V6z M9 12l2 2 4-4",
  refresh: "M20 12a8 8 0 1 1-2.3-5.6M20 4.5v4h-4",
  more: "M6 12h.01M12 12h.01M18 12h.01",
  lock: "M7 11V8a5 5 0 0 1 10 0v3M5.5 11h13v9h-13z",
  flag: "M5 21V4M5 4h11l-2 4 2 4H5",
  tool: "M14.5 6.5a4 4 0 0 0-5.3 5.3L4 17l3 3 5.2-5.2a4 4 0 0 0 5.3-5.3l-2.6 2.6-2.3-.7-.7-2.3z",
  tent: "M3 20h18M12 4 4 20M12 4l8 16M12 4v0M10 20l2-5 2 5",
  luggage: "M7 7h10a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1zM9.5 7V4h5v3M10 11v6M14 11v6",
  broom: "M14 3 9.5 10M6 11h7l2 3-1 7H7l-2-7zM9 21v-4M12 21v-4",
  cup: "M5 8h11v6a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3.5v2M11 3.5v2M14 3.5v2",
  grid: "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
  camera: "M4 8h3l2-3h6l2 3h3v11H4zM12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z",
  chat: "M5 5h14a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H10l-5 4v-4H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z",
  send: "M4 12 20 4l-5 16-3.5-6.5zM11.5 13.5 20 4",
  handshake: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2M7 3.5 4 6M17 3.5 20 6",
};

/** 별칭 — 우리 코드에서 쓰는 이름 */
const ALIAS: Record<string, string> = { dots: "more" };

type IconProps = {
  name: string;
  size?: number;
  strokeWidth?: number;
};

export function Icon({ name, size = 24, strokeWidth = 1.8 }: IconProps) {
  const d = PATHS[ALIAS[name] ?? name] ?? PATHS.info;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {d.split(/(?=M)/).map((seg, i) => (
        <path key={i} d={seg} />
      ))}
    </svg>
  );
}
