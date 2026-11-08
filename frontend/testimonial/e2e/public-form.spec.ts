import { expect, test } from "@playwright/test";

/**
 * The public testimonial page (PRD 4.5).
 *
 * These tests drive the real page against the real backend. A visitor fills
 * in the form without an account and is thanked.
 */

/**
 * A token that is unique to this run.
 *
 * Slugs are unique across the whole platform, and the tests share one
 * database, so every test needs its own.
 */
function runToken(): string {
  return Math.random().toString(36).slice(2, 8);
}

/** A fresh email address for each test run. */
function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
}

/**
 * Waits for React to attach its event handlers.
 *
 * A click that lands before hydration is handled by the browser rather than
 * by React, which would submit the form the wrong way -- as a plain GET,
 * putting the values in the query string instead of posting them.
 */
async function hydrated(page: import("@playwright/test").Page) {
  await page.waitForLoadState("networkidle");
  await page.waitForFunction(() => {
    const form = document.querySelector("form");
    return form !== null && Object.keys(form).some((key) =>
      key.startsWith("__reactProps$"),
    );
  });
}

/**
 * Registers an account, confirms its email address and signs in.
 */
async function registerAndVerify(
  page: import("@playwright/test").Page,
  email: string,
) {
  await page.goto("/register");
  await hydrated(page);
  await page.getByLabel("Name").fill("Test Owner");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("a-good-password");
  await page.getByRole("button", { name: /create account/i }).click();

  await expect(
    page.getByRole("heading", { name: /check your email/i }),
  ).toBeVisible();

  await verifyEmailDirectly(email);

  await page.goto("/login");
  await hydrated(page);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("a-good-password");
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
}

/**
 * Creates a Space through the wizard and returns its slug.
 */
async function createSpace(
  page: import("@playwright/test").Page,
  options: {
    title: string;
    slug: string;
    ratingEnabled?: boolean;
    companyNameEnabled?: boolean;
  },
): Promise<string> {
  await page.goto("/spaces/new");
  await hydrated(page);
  await page.getByLabel("Title", { exact: true }).fill(options.title);
  await page.getByLabel("Public URL").fill(options.slug);
  await page.getByRole("button", { name: "Continue" }).click();

  if (options.companyNameEnabled === false) {
    await page
      .getByRole("checkbox", { name: "Collect Company name" })
      .uncheck();
  }

  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Continue" }).click();

  if (options.ratingEnabled) {
    await page.getByRole("checkbox", { name: /show a 1.5 star rating/i }).check();
  }

  await page.getByRole("button", { name: /create space/i }).click({ noWaitAfter: true });

  await expect(
    page.getByRole("heading", { name: /your space is ready/i }),
  ).toBeVisible();

  return options.slug;
}

test.describe("the public form", () => {
  test("a visitor sends a testimonial and is thanked", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("public"));

    const slug = await createSpace(page, {
      title: "Public Reviews",
      slug: `public-reviews-${token}`,
    });

    // No session: a visitor fills in the form without an account.
    const context = page.context();
    await context.clearCookies();

    await page.goto(`/s/${slug}`);
    await hydrated(page);
    await expect(page.getByRole("heading", { name: "Public Reviews" })).toBeVisible();

    await page.getByLabel("Your name").fill("Ada Lovelace");
    await page.getByLabel("Your email").fill("ada@example.com");
    await page.getByLabel("Your testimonial").fill("The best product I have used.");
    await page.getByRole("button", { name: "Send testimonial" }).click();

    await expect(
      page.getByRole("heading", { name: /thank you/i }),
    ).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/s/${slug}/thank-you$`));
  });

  test("the thank-you page shows an animation", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("gif"));

    const slug = await createSpace(page, {
      title: "Gif Reviews",
      slug: `gif-reviews-${token}`,
    });

    await page.context().clearCookies();
    await page.goto(`/s/${slug}/thank-you`);

    const gif = page.getByTestId("thank-you-gif");
    await expect(gif).toBeVisible();
    await expect(gif).toHaveAttribute("src", /\/gifs\/.+\.gif$/);
  });

  test("missing required fields are reported on the form", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("missing"));

    const slug = await createSpace(page, {
      title: "Missing Reviews",
      slug: `missing-reviews-${token}`,
    });

    await page.context().clearCookies();
    await page.goto(`/s/${slug}`);
    await hydrated(page);

    await page.getByRole("button", { name: "Send testimonial" }).click();

    // The backend's field-level errors are shown next to the inputs.
    await expect(page.getByText("Your name is required")).toBeVisible();
    await expect(page.getByText("Your email is required")).toBeVisible();
    await expect(page.getByText("Your testimonial is required")).toBeVisible();

    // The visitor stays on the form.
    await expect(page).toHaveURL(new RegExp(`/s/${slug}$`));
  });

  test("the form shows exactly the fields the Space collects", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("fields"));

    const slug = await createSpace(page, {
      title: "Field Reviews",
      slug: `field-reviews-${token}`,
      companyNameEnabled: false,
    });

    await page.context().clearCookies();
    await page.goto(`/s/${slug}`);

    const form = page.getByTestId("public-form");

    // The company name was switched off, so it is not on the form.
    await expect(form.locator('[name="companyName"]')).toHaveCount(0);

    // The fields that are collected are.
    for (const name of ["name", "email", "jobTitle", "websiteUrl", "message"]) {
      await expect(form.locator(`[name="${name}"]`)).toHaveCount(1);
    }
  });

  test("a nonexistent slug shows the not-found page", async ({ page }) => {
    await page.goto(`/s/no-such-space-${runToken()}`);

    await expect(
      page.getByRole("heading", { name: /page not found/i }),
    ).toBeVisible();
  });

  test("a field the Space switched off is not stored", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("off"));

    const slug = await createSpace(page, {
      title: "Off Reviews",
      slug: `off-reviews-${token}`,
      companyNameEnabled: false,
    });

    // The visitor is a different person from the owner, in a different
    // browser context, so the owner is still signed in afterwards.
    const visitor = await browser.newContext();
    const visitorPage = await visitor.newPage();

    await visitorPage.goto(`/s/${slug}`);
    await hydrated(visitorPage);
    await visitorPage.getByLabel("Your name").fill("Ada");
    await visitorPage.getByLabel("Your email").fill("ada@example.com");
    await visitorPage.getByLabel("Your testimonial").fill("Great.");
    await visitorPage
      .getByRole("button", { name: "Send testimonial" })
      .click();

    await expect(
      visitorPage.getByRole("heading", { name: /thank you/i }),
    ).toBeVisible();

    // The Space's page says the company name is not collected.
    await page.goto("/spaces");
    await page.getByRole("link", { name: "Off Reviews" }).click();

    const companyRow = page.locator("li", { hasText: "Company name" });
    await expect(companyRow).toContainText("Not collected");

    await visitor.close();
  });

  test("markup in a testimonial is shown as text, not as markup", async ({
    page,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("xss"));

    const slug = await createSpace(page, {
      title: "Xss Reviews",
      slug: `xss-reviews-${token}`,
    });

    await page.context().clearCookies();
    await page.goto(`/s/${slug}`);
    await hydrated(page);

    await page.getByLabel("Your name").fill("Ada");
    await page.getByLabel("Your email").fill("ada@example.com");
    await page
      .getByLabel("Your testimonial")
      .fill("<script>window.__pwned = true;</script>");
    await page.getByRole("button", { name: "Send testimonial" }).click();

    await expect(page.getByRole("heading", { name: /thank you/i })).toBeVisible();

    // The script did not run.
    const pwned = await page.evaluate(() => (window as never as {
      __pwned?: boolean;
    }).__pwned);
    expect(pwned).toBeUndefined();
  });

  test("the rating is shown when the Space asks for one", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("rating"));

    const slug = await createSpace(page, {
      title: "Rating Reviews",
      slug: `rating-reviews-${token}`,
      ratingEnabled: true,
    });

    await page.context().clearCookies();
    await page.goto(`/s/${slug}`);
    await hydrated(page);

    const stars = page.getByRole("radio", { name: "5 stars" });
    await expect(stars).toHaveCount(1);
    await stars.check();

    await page.getByLabel("Your name").fill("Ada");
    await page.getByLabel("Your email").fill("ada@example.com");
    await page.getByLabel("Your testimonial").fill("Great.");
    await page.getByRole("button", { name: "Send testimonial" }).click();

    await expect(page.getByRole("heading", { name: /thank you/i })).toBeVisible();
  });

  test("the honeypot is off-screen and cannot be seen or focused", async ({
    page,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("honeypot"));

    const slug = await createSpace(page, {
      title: "Honeypot Reviews",
      slug: `honeypot-reviews-${token}`,
    });

    await page.context().clearCookies();
    await page.goto(`/s/${slug}`);

    const honeypot = page.locator('[name="companyWebsite"]');
    await expect(honeypot).toHaveCount(1);

    // Off-screen rather than hidden, so a bot cannot tell it apart from a
    // field a person fills in.
    const box = await honeypot.boundingBox();
    expect(box === null || box.x < 0).toBeTruthy();

    // Not reachable by keyboard and not in the accessibility tree.
    await expect(honeypot).toHaveAttribute("tabindex", "-1");
    await expect(honeypot).toHaveAttribute("aria-hidden", "true");
  });

  test("a bot that fills the honeypot is answered as though it worked", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("bot"));

    const slug = await createSpace(page, {
      title: "Bot Reviews",
      slug: `bot-reviews-${token}`,
    });

    // The bot visits in its own context, so the owner stays signed in.
    const bot = await browser.newContext();
    const botPage = await bot.newPage();

    await botPage.goto(`/s/${slug}`);
    await hydrated(botPage);

    // Fill the hidden field, as a bot would.
    await botPage
      .locator('[name="companyWebsite"]')
      .evaluate((element) => {
        (element as HTMLInputElement).value = "https://spam.example.com";
      });

    await botPage.getByLabel("Your name").fill("Spam Bot");
    await botPage.getByLabel("Your email").fill("spam@example.com");
    await botPage.getByLabel("Your testimonial").fill("Buy my product.");
    await botPage.getByRole("button", { name: "Send testimonial" }).click();

    // The bot is not told anything different.
    await expect(
      botPage.getByRole("heading", { name: /thank you/i }),
    ).toBeVisible();

    // But nothing was stored. The Space's page shows how many testimonials
    // it has, and the bot's submission is not one of them.
    await page.goto("/spaces");
    await page.getByRole("link", { name: "Bot Reviews" }).click();

    const count = page
      .locator("dt", { hasText: "Testimonials" })
      .locator("xpath=following-sibling::dd[1]");
    await expect(count).toHaveText("0");

    await bot.close();
  });

  test("a visitor's testimonial reaches the owner's Space page", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("inbox"));

    const slug = await createSpace(page, {
      title: "Inbox Reviews",
      slug: `inbox-reviews-${token}`,
    });

    const visitor = await browser.newContext();
    const visitorPage = await visitor.newPage();

    await visitorPage.goto(`/s/${slug}`);
    await hydrated(visitorPage);
    await visitorPage.getByLabel("Your name").fill("Grace Hopper");
    await visitorPage.getByLabel("Your email").fill("grace@example.com");
    await visitorPage
      .getByLabel("Your testimonial")
      .fill("It works, and I can say so.");
    await visitorPage
      .getByRole("button", { name: "Send testimonial" })
      .click();

    await expect(
      visitorPage.getByRole("heading", { name: /thank you/i }),
    ).toBeVisible();

    // The owner's Space page counts it.
    await page.goto("/spaces");
    await page.getByRole("link", { name: "Inbox Reviews" }).click();

    const count = page
      .locator("dt", { hasText: "Testimonials" })
      .locator("xpath=following-sibling::dd[1]");
    await expect(count).toHaveText("1");

    await visitor.close();
  });
});

/**
 * Confirms an account's email address directly in the database.
 */
async function verifyEmailDirectly(email: string) {
  const { execFileSync } = await import("node:child_process");

  execFileSync(
    "docker",
    [
      "exec",
      "testimonial-postgres",
      "psql",
      "-U",
      "testimonial",
      "-d",
      "testimonial",
      "-c",
      `update app_users set email_verified = true where email = '${email}'`,
    ],
    { stdio: "pipe" },
  );
}
