export const WEB_USER_ROLES = ["DEVELOPER", "SERVICE", "ADMIN"] as const;

export type WebUserRole = (typeof WEB_USER_ROLES)[number];

export interface WebUserRecord {
  readonly id: string;
  readonly email: string;
  readonly username: string;
  readonly role: WebUserRole;
  readonly linkedPlayerUuid: string | null;
}

export interface CreateWebUserPayload {
  email: string;
  password: string;
  role: WebUserRole;
}

export interface UpdateWebUserPayload {
  email?: string;
  password?: string;
  username?: string;
  role?: WebUserRole;
}
