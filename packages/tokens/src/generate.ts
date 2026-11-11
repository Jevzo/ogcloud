import { mkdirSync, writeFileSync } from "node:fs";
import { renderCss, stylesheetUrl } from "./css.ts";

mkdirSync(new URL(".", stylesheetUrl), { recursive: true });
writeFileSync(stylesheetUrl, renderCss());
