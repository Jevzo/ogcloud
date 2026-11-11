import { ApiError } from "@ogcloud/api-client";

/** The answers the dashboard tells apart; every other one is reported in the API's own words. */
export const HttpStatus = {
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  SERVER_ERROR: 500,
} as const;

/** The status an API call answered with, or none for a failure that never got an answer. */
export function statusOf(error: unknown): number | undefined {
  return error instanceof ApiError ? error.status : undefined;
}
