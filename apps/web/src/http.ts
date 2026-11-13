import { ApiError } from "@ogcloud/api-client";
import { TokenRequestFailed } from "@ogcloud/auth";

/** The answers the dashboard tells apart; every other one is reported in the API's own words. */
export const HttpStatus = {
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  TOO_MANY_REQUESTS: 429,
  SERVER_ERROR: 500,
} as const;

/** The status an API call or its token renewal answered with, or none for a failure that never got an answer. */
export function statusOf(error: unknown): number | undefined {
  return error instanceof ApiError || error instanceof TokenRequestFailed
    ? error.status
    : undefined;
}
