/**
 * 카카오맵 JavaScript SDK 로더 (도메인 가이드 P12·P13).
 * 키는 VITE_KAKAO_MAP_KEY(JavaScript 키). 키가 없거나 불러오기에 실패하면 화면은 도식 지도로 대체한다.
 */

/** SDK에서 쓰는 부분만 최소로 타입을 둔다 */
export type KakaoLatLng = object;
export type KakaoBounds = { extend(position: KakaoLatLng): void };
export type KakaoOverlay = { setMap(map: KakaoMap | null): void };
export type KakaoMap = {
  setBounds(bounds: KakaoBounds, top?: number, right?: number, bottom?: number, left?: number): void;
  setCenter(position: KakaoLatLng): void;
  setLevel(level: number): void;
  relayout(): void;
};
export type KakaoMaps = {
  load(callback: () => void): void;
  LatLng: new (lat: number, lng: number) => KakaoLatLng;
  LatLngBounds: new () => KakaoBounds;
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap;
  CustomOverlay: new (options: {
    position: KakaoLatLng;
    content: HTMLElement;
    yAnchor?: number;
    clickable?: boolean;
  }) => KakaoOverlay;
};

declare global {
  interface Window {
    kakao?: { maps: KakaoMaps };
  }
}

export const KAKAO_MAP_KEY: string = (import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined) ?? "";

/** SDK 응답이 없을 때 실패로 보는 시간 */
const LOAD_TIMEOUT_MS = 8000;

let pending: Promise<KakaoMaps> | null = null;

/** SDK를 한 번만 불러온다. 실패하면 다음 호출에서 다시 시도할 수 있다 */
export function loadKakaoMaps(key: string = KAKAO_MAP_KEY): Promise<KakaoMaps> {
  if (!key) return Promise.reject(new Error("KAKAO_MAP_KEY_MISSING"));
  const ready = window.kakao?.maps;
  if (ready?.LatLng) return Promise.resolve(ready);

  pending ??= new Promise<KakaoMaps>((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error("KAKAO_MAP_TIMEOUT")), LOAD_TIMEOUT_MS);
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
    script.async = true;
    script.onload = () => {
      const maps = window.kakao?.maps;
      if (!maps) {
        window.clearTimeout(timer);
        reject(new Error("KAKAO_MAP_LOAD_FAILED"));
        return;
      }
      maps.load(() => {
        window.clearTimeout(timer);
        resolve(maps);
      });
    };
    script.onerror = () => {
      window.clearTimeout(timer);
      script.remove();
      reject(new Error("KAKAO_MAP_LOAD_FAILED"));
    };
    document.head.appendChild(script);
  }).catch((e: unknown) => {
    pending = null;
    throw e;
  });
  return pending;
}
