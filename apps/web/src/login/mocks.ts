import type { LoginState, TotpSetup } from "@ogcloud/api-client";
import type { LoginSource } from "./login-app.tsx";

const STEPS = ["CREDENTIALS", "TOTP", "PASSKEY", "ENROLMENT", "DONE"] as const;
const SETUP: TotpSetup = {
  secret: "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
  uri: "otpauth://totp/OgCloud:mira?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=OgCloud",
};

/** Fixtures for screenshots: `?step=` picks the step, and `?error` the refused variant. */
export function mockLoginSource(query: URLSearchParams): LoginSource {
  const requested = query.get("step")?.toUpperCase();
  const step =
    STEPS.find((candidate) => candidate === requested) ?? "CREDENTIALS";
  const state: LoginState = {
    step,
    csrf: {
      headerName: "X-CSRF-TOKEN",
      parameterName: "_csrf",
      token: "mocks",
    },
  };
  return { state: async () => state, totpSetup: async () => SETUP };
}
