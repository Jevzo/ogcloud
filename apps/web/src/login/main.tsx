import "../styles.css";
import { loginState, mfaSetup } from "@ogcloud/api-client";
import { createI18n } from "@ogcloud/i18n";
import { UiProvider } from "@ogcloud/ui";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { I18nextProvider } from "react-i18next";
import { applyPreferences, readPreferences } from "../preferences.ts";
import { LoginApp, type LoginSource } from "./login-app.tsx";

const API: LoginSource = {
  state: async () => (await loginState()).data,
  totpSetup: async () => (await mfaSetup()).data,
};

async function sourceFor(): Promise<LoginSource> {
  // Compared inline, so production builds drop the fixtures entirely.
  if (import.meta.env.MODE !== "mocks") return API;
  const { mockLoginSource } = await import("./mocks.ts");
  return mockLoginSource(new URLSearchParams(window.location.search));
}

async function start(container: HTMLElement) {
  applyPreferences(readPreferences());
  const source = await sourceFor();
  createRoot(container).render(
    <StrictMode>
      <I18nextProvider i18n={createI18n()}>
        <UiProvider>
          <LoginApp source={source} />
        </UiProvider>
      </I18nextProvider>
    </StrictMode>,
  );
}

const container = document.getElementById("root");
if (container === null) throw new Error("index.html has no #root");
void start(container);
