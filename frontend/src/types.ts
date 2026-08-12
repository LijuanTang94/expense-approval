// Mirrors the Spring Boot DTOs (com.sandy.expense.web.dto).

export type Role = "EMPLOYEE" | "MANAGER" | "FINANCE";

export type RequestStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "MANAGER_APPROVED"
  | "FINANCE_APPROVED"
  | "REJECTED";

export interface UserView {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  departmentId: number | null;
  departmentName: string | null;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserView;
}

export interface Department {
  id: number;
  name: string;
}

export interface ItemView {
  id: number;
  description: string;
  category: string;
  amount: number;
  incurredOn: string;
}

export interface ApprovalView {
  id: number;
  actorId: number;
  actorName: string;
  action: string;
  fromStatus: string;
  toStatus: string;
  comment: string;
  createdAt: string;
}

export interface RequestSummary {
  id: number;
  title: string;
  status: RequestStatus;
  totalAmount: number;
  currency: string;
  requesterName: string;
  departmentName: string;
  createdAt: string;
}

export interface RequestDetail {
  id: number;
  title: string;
  description: string;
  status: RequestStatus;
  totalAmount: number;
  currency: string;
  requesterId: number;
  requesterName: string;
  departmentName: string;
  submittedAt: string | null;
  createdAt: string;
  items: ItemView[];
  approvals: ApprovalView[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ItemInput {
  description: string;
  category: string;
  amount: number;
  incurredOn: string;
}

export interface CreateRequestInput {
  title: string;
  description: string;
  currency: string;
  items: ItemInput[];
}
