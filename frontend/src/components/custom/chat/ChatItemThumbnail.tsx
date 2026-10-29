import { useState } from "react";
import { Icon } from "@/components/ui/icon";

/** 사진이 없거나 읽을 수 없으면 채팅 기본 아이콘을 표시한다. */
export function ChatItemThumbnail({ src, title, className, placeholderClassName }: {
  src?: string | null;
  title: string;
  className: string;
  placeholderClassName: string;
}) {
  const [failed, setFailed] = useState(false);
  if (src && !failed) {
    return <img src={src} alt={`${title} 사진`} className={className}
      loading="lazy" onError={() => setFailed(true)} />;
  }
  return <span className={`${className} ${placeholderClassName}`} role="img" aria-label={`${title} 사진 없음`}>
    <Icon name="chat" size={18} />
  </span>;
}
