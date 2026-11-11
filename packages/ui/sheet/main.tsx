import { createI18n } from "@ogcloud/i18n";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { I18nextProvider } from "react-i18next";
import { UiProvider } from "../src/index.ts";
import { Sheet } from "./sheet.tsx";
import "./sheet.css";

// The screenshot run picks the theme and density per page, as a user's settings would.
const params = new URLSearchParams(window.location.search);
const root = document.documentElement;
root.dataset.theme = params.get("theme") ?? "light";
root.dataset.density = params.get("density") ?? "compact";

const container = document.getElementById("root");
if (container === null) throw new Error("index.html has no #root");

createRoot(container).render(
  <StrictMode>
    <I18nextProvider i18n={createI18n()}>
      <UiProvider>
        <Sheet />
      </UiProvider>
    </I18nextProvider>
  </StrictMode>,
);
