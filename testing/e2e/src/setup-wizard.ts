import { chromium } from "@playwright/test";
import type { UserLogin } from "./login.ts";
import type { Target } from "./target.ts";
import { base32Decode, code, step } from "./totp.ts";

interface NewOwner {
  readonly username: string;
  readonly email: string;
  readonly password: string;
}

// Generous for a cold cluster, where the dashboard's first answers and the sign-in redirects take a while.
const STEP_TIMEOUT_MS = 60_000;

/**
 * Sets up a fresh install as its owner would, in Chromium (T-0710): the dashboard sends a signed-out visitor to the
 * setup, which takes the setup token and the owner, signs in with an authenticator enrolled on the way (D-0097) and
 * finishes on the EULA and the starter groups, which the seed skips since it makes its own. Fails on any CSP refusal
 * (D-0219) or script error. `onOwnerCreated` keeps the login as soon as the account exists, so a later failure leaves
 * an install the seed can sign in to. Returns the owner's login with the authenticator's secret.
 */
export async function setUpThroughWizard(
  target: Target,
  owner: NewOwner,
  setupToken: string,
  onOwnerCreated: (login: UserLogin) => Promise<void>,
  now: () => Date = () => new Date(),
): Promise<UserLogin> {
  const browser = await chromium.launch();
  try {
    // k3d and minikube serve a certificate from the internal CA, which this browser does not know.
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    context.setDefaultTimeout(STEP_TIMEOUT_MS);
    const page = await context.newPage();
    const refused: string[] = [];
    page.on("console", (message) => {
      if (message.text().includes("Content Security Policy"))
        refused.push(message.text());
    });
    const crashed: string[] = [];
    page.on("pageerror", (error) => crashed.push(error.message));

    await page.goto(target.origin);
    await page.waitForURL("**/setup");
    await page.getByLabel("Setup token", { exact: true }).fill(setupToken);
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByLabel("Username", { exact: true }).fill(owner.username);
    await page.getByLabel("Email", { exact: true }).fill(owner.email);
    await page.getByLabel("New password", { exact: true }).fill(owner.password);
    await page
      .getByLabel("Repeat the new password", { exact: true })
      .fill(owner.password);
    await page.getByRole("button", { name: "Create owner account" }).click();
    const signIn = page.getByRole("button", { name: "Sign in" });
    await signIn.waitFor();
    await onOwnerCreated({
      username: owner.username,
      password: owner.password,
    });
    await signIn.click();

    // The login pages (D-0212): the password, then the authenticator owners must have.
    await page.getByLabel("Username or email").fill(owner.username);
    await page.getByLabel("Password", { exact: true }).fill(owner.password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.getByRole("button", { name: "Authenticator app" }).click();
    const secret = (
      await page.locator("span.font-mono").first().textContent()
    )?.trim();
    if (secret === undefined || secret === "")
      throw new Error("The authenticator setup shows no secret");
    await page
      .getByLabel("Code from the app")
      .fill(code(base32Decode(secret), step(now())));
    await page.getByRole("button", { name: "Confirm" }).click();

    // Back in the dashboard: the EULA, which the chart may have accepted already (D-0100), then the starter groups,
    // which the seed skips for its own.
    await page.waitForURL("**/welcome");
    const eula = page.getByRole("button", {
      name: /^(Continue|Accept and continue)$/,
    });
    await eula.waitFor();
    if ((await eula.textContent())?.includes("Accept") === true) {
      await page.getByRole("checkbox").check();
    }
    await eula.click();
    await page.getByRole("button", { name: /^(Skip|Finish)$/ }).click();
    await page.waitForURL(`${target.origin}/`);
    await page.getByRole("navigation").first().waitFor();

    if (refused.length > 0)
      throw new Error(`The CSP refused: ${refused.join("; ")}`);
    if (crashed.length > 0)
      throw new Error(`The pages threw: ${crashed.join("; ")}`);
    return {
      username: owner.username,
      password: owner.password,
      totpSecret: secret,
    };
  } finally {
    await browser.close();
  }
}
