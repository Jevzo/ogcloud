import "./styles.css";
import {
  type ApiConnection,
  ApiConnectionContext,
} from "@ogcloud/api-client/react";
import { createI18n } from "@ogcloud/i18n";
import { UiProvider } from "@ogcloud/ui";
import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { I18nextProvider } from "react-i18next";
import {
  connectionFor,
  createQueryClient,
  createSession,
  signIn,
} from "./auth.ts";
import { applyPreferences, readPreferences } from "./preferences.ts";
import { router } from "./router.tsx";
import { SessionContext } from "./session-context.ts";

const MOCK_CONNECTION: ApiConnection = { accessToken: async () => "mocks" };

// Compared inline, so production builds drop the mocks and their chunk entirely.
const mocksMode = import.meta.env.MODE === "mocks";

async function start(container: HTMLElement) {
  applyPreferences(readPreferences());
  const session = mocksMode ? null : createSession();
  const queryClient = createQueryClient(session, () => {
    if (session !== null) void signIn(session);
  });
  if (mocksMode) {
    const { startMocks } = await import("./mocks/browser.ts");
    await startMocks();
  }
  createRoot(container).render(
    <StrictMode>
      <I18nextProvider i18n={createI18n()}>
        <QueryClientProvider client={queryClient}>
          <ApiConnectionContext.Provider
            value={session === null ? MOCK_CONNECTION : connectionFor(session)}
          >
            <SessionContext.Provider value={session}>
              <UiProvider>
                <RouterProvider router={router} />
              </UiProvider>
            </SessionContext.Provider>
          </ApiConnectionContext.Provider>
        </QueryClientProvider>
      </I18nextProvider>
    </StrictMode>,
  );
}

const container = document.getElementById("root");
if (container === null) throw new Error("index.html has no #root");
void start(container);
