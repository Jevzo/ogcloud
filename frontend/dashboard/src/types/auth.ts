export interface AuthUser {
  id: string;
  email: string;
  username: string;
  role: string;
  linkedPlayerUuid: string | null;
}

export interface AuthSession {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: AuthUser;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface ApiErrorResponse {
  status: number;
  message: string;
  details: readonly string[];
}
