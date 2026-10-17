export type AppointmentKind = "PICKUP" | "RETURN";

export type ApiAppointmentProposal = {
  id: string;
  slotId: string;
  proposerId: string;
  kind: AppointmentKind;
  scheduledAt: string;
  place: {
    id: string;
    communityId: string;
    name: string;
    latitude?: number;
    longitude?: number;
    guide?: string;
  };
  status: "PROPOSED" | "CONFIRMED" | "SUPERSEDED" | "WITHDRAWN";
  acceptedBy?: string;
  acceptedAt?: string;
};

export type ApiAppointmentSlot = {
  id: string;
  rentalId: string;
  kind: AppointmentKind;
  version: number;
  confirmedProposal?: ApiAppointmentProposal;
  pendingProposal?: ApiAppointmentProposal;
  allowedActions: Array<"PROPOSE" | "ACCEPT" | "WITHDRAW">;
};
