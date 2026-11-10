import assert from "node:assert/strict";
import { test } from "node:test";
import { ApiError, request } from "./request.ts";

function answering(
  status: number,
  body: string,
  type = "application/problem+json",
) {
  return async () =>
    new Response(body, { status, headers: { "content-type": type } });
}

test("a 2xx answer comes back as orval's shape", async () => {
  const response = await request<{ data: unknown; status: number }>(
    "/api/v1/groups/lobby",
    {
      fetch: answering(200, '{"id":"lobby"}', "application/json"),
    },
  );

  assert.equal(response.status, 200);
  assert.deepEqual(response.data, { id: "lobby" });
});

// The generated types name only success bodies, so an error must never come back as one (D-0189).
test("any other answer throws an ApiError with the ProblemDetail", async () => {
  await assert.rejects(
    request("/api/v1/groups/lobby", {
      method: "DELETE",
      fetch: answering(
        409,
        '{"title":"Conflict","status":409,"detail":"The group is in use"}',
      ),
    }),
    (error: unknown) =>
      error instanceof ApiError &&
      error.status === 409 &&
      error.problem?.detail === "The group is in use" &&
      error.message ===
        "DELETE /api/v1/groups/lobby answered 409: The group is in use",
  );
});

test("an error without a ProblemDetail still names its status", async () => {
  await assert.rejects(
    request("/api/v1/overview", {
      fetch: answering(502, "Bad Gateway", "text/plain"),
    }),
    (error: unknown) =>
      error instanceof ApiError &&
      error.status === 502 &&
      error.problem === undefined &&
      error.message === "GET /api/v1/overview answered 502: Bad Gateway",
  );
});
