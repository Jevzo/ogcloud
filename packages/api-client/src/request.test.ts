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

test("an error whose body only claims to be JSON still names its status", async () => {
  await assert.rejects(
    request("/api/v1/overview", {
      fetch: answering(502, "<html>Bad Gateway</html>", "application/json"),
    }),
    (error: unknown) =>
      error instanceof ApiError &&
      error.status === 502 &&
      error.problem === undefined,
  );
});

test("a call carries the bearer token only when it has one", async () => {
  const sent: (string | null)[] = [];
  const recording = async (_url: string, init: RequestInit) => {
    sent.push(new Headers(init.headers).get("Authorization"));
    return new Response("{}", {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  await request("/api/v1/setup", { fetch: recording });
  await request("/api/v1/me", { fetch: recording, token: "access-1" });

  assert.deepEqual(sent, [null, "Bearer access-1"]);
});
