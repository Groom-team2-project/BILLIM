import type { ApiAppointmentProposal } from "@/types/appointment-api";
import type { Appointment } from "@/types/chat";

export function appointmentDateLabel(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  const parts = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", month: "numeric", day: "numeric", weekday: "short",
  }).formatToParts(new Date(Date.UTC(year, month - 1, day, 12)));
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${value("month")}/${value("day")}(${value("weekday")})`;
}

export function appointmentInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00+09:00`).toISOString();
}

export function appointmentView(proposal: ApiAppointmentProposal): Appointment {
  const date = new Date(proposal.scheduledAt);
  const localDate = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(date);
  return {
    kind: proposal.kind === "PICKUP" ? "전달" : "반납",
    date: appointmentDateLabel(localDate),
    time: new Intl.DateTimeFormat("ko-KR", {
      timeZone: "Asia/Seoul", hour: "numeric", minute: "2-digit", hour12: true,
    }).format(date),
    place: proposal.place.name,
  };
}
