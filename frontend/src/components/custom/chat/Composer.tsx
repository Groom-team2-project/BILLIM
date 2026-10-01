import { useState } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/custom/chat/Composer.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type ComposerProps = {
  /** 차단 상태면 입력 비활성 */
  blocked?: boolean;
  /** + 버튼 — 약속 잡기 시트 열기 */
  onPlus?: () => void;
  onSend?: (text: string) => void;
};

export function Composer({ blocked, onPlus, onSend }: ComposerProps) {
  const [value, setValue] = useState("");
  if (blocked) {
    return (
      <div className={c("composer composer--off t-caption")}>
        <Icon name="lock" size={16} />
        차단한 이웃이에요. 차단을 풀면 다시 대화할 수 있어요.
      </div>
    );
  }
  const send = () => {
    const text = value.trim();
    if (!text) return;
    onSend?.(text);
    setValue("");
  };
  return (
    <form
      className={c("composer")}
      onSubmit={(e) => {
        e.preventDefault();
        send();
      }}
    >
      <button type="button" className={c("composer-plus")} aria-label="약속 잡기 · 사진 · 장소" onClick={onPlus}>
        <Icon name="plus" size={22} />
      </button>
      <label className={c("composer-field")}>
        <span className={c("sr-only")}>메시지</span>
        <input
          type="text"
          placeholder="메시지 보내기"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </label>
      <button type="submit" className={c("composer-send")} aria-label="보내기" disabled={!value.trim()}>
        <Icon name="send" size={20} />
      </button>
    </form>
  );
}
