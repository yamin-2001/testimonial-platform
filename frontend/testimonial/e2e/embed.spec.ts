import { expect, test } from "@playwright/test";

/**
 * The Wall of Love and the embed (PRD 4.8).
 *
 * These tests drive the real embed against the real backend. A visitor
 * submits a testimonial, the owner puts it on the Wall of Love, and the
 * widget shows it on a page that has nothing to do with this app.
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
 * The embed configuration page has no form -- its options are buttons and
 * checkboxes -- so the wait looks for any element that React has taken
 * ownership of rather than for a form specifically.
 */
async function hydrated(page: import("@playwright/test").Page) {
  await page.waitForLoadState("networkidle");
  await page.waitForFunction(() => {
    const root = document.querySelector("#__next") ?? document.body;
    return Object.keys(root).some((key) => key.startsWith("__reactProps$"));
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
 * Creates a Space through the wizard and returns its slug and id.
 */
async function createSpace(
  page: import("@playwright/test").Page,
  options: { title: string; slug: string },
): Promise<{ slug: string; id: string }> {
  await page.goto("/spaces/new");
  await hydrated(page);
  await page.getByLabel("Title", { exact: true }).fill(options.title);
  await page.getByLabel("Public URL").fill(options.slug);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Continue" }).click();

  await page
    .getByRole("button", { name: /create space/i })
    .click({ noWaitAfter: true });

  await expect(
    page.getByRole("heading", { name: /your space is ready/i }),
  ).toBeVisible();

  const id = page.url().match(/\/spaces\/([0-9a-f-]{36})\/created/)?.[1];
  expect(id).toBeTruthy();

  return { slug: options.slug, id: id as string };
}

/**
 * Sends a testimonial to a Space as a visitor, in a browser of its own.
 */
async function submitTestimonial(
  browser: import("@playwright/test").Browser,
  slug: string,
  details: { name: string; email: string; message: string },
) {
  const visitor = await browser.newContext();
  const visitorPage = await visitor.newPage();

  await visitorPage.goto(`/s/${slug}`);
  await hydrated(visitorPage);
  await visitorPage.getByLabel("Your name").fill(details.name);
  await visitorPage.getByLabel("Your email").fill(details.email);
  await visitorPage.getByLabel("Your testimonial").fill(details.message);

  await visitorPage
    .getByRole("button", { name: "Send testimonial" })
    .click();

  await expect(
    visitorPage.getByRole("heading", { name: /thank you/i }),
  ).toBeVisible();

  await visitor.close();
}

/**
 * Puts a testimonial on the Wall of Love, from the owner's inbox.
 */
async function addToWallOfLove(
  page: import("@playwright/test").Page,
  name: string,
) {
  await page.goto("/inbox");
  await hydrated(page);

  const row = page
    .getByTestId("inbox-list")
    .getByRole("listitem")
    .filter({ hasText: name });

  await expect(row).toHaveCount(1);

  await row.getByRole("button", { name: "Add to Wall of Love" }).click();

  // The badge appears once the change has been applied.
  await expect(row.locator("span", { hasText: /^Wall of Love$/ })).toBeVisible();
}

/**
 * Opens the embed configuration page for a Space.
 */
async function openEmbedConfig(
  page: import("@playwright/test").Page,
  spaceId: string,
) {
  await page.goto(`/spaces/${spaceId}/embed`);
  await hydrated(page);
  await expect(page.getByTestId("embed-preview")).toBeVisible();
}

/**
 * A plain HTML page that loads the embed, with nothing else on it.
 *
 * It is served as a data URL so that it really is a page on another site:
 * the embed script has to work without this app.
 */
function hostPage(snippet: string): string {
  return `<!doctype html>
<html>
  <head><meta charset="utf-8"><title>A customer page</title></head>
  <body>
    <h1>Our customers</h1>
    <p>Some words about the product.</p>
    ${snippet}
  </body>
</html>`;
}

/**
 * Loads the embed into a plain page and waits for it to render.
 *
 * The page is opened in a context of its own, so the host page is a
 * different origin from the dashboard and cannot use its cookies.
 */
async function renderEmbed(
  browser: import("@playwright/test").Browser,
  snippet: string,
): Promise<import("@playwright/test").Page> {
  const context = await browser.newContext();
  const host = await context.newPage();

  await host.setContent(hostPage(snippet), { waitUntil: "domcontentloaded" });

  return host;
}

/**
 * The snippet for a Space, as the configuration page shows it.
 */
async function snippetFor(
  page: import("@playwright/test").Page,
  spaceId: string,
): Promise<string> {
  await openEmbedConfig(page, spaceId);
  return (await page.getByTestId("embed-snippet").inputValue()).trim();
}

test.describe("the embed", () => {
  test("pasting the snippet into a plain page renders the testimonials", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-basic"));

    const { slug, id } = await createSpace(page, {
      title: "Embed Reviews",
      slug: `embed-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "It works, and I can say so.",
    });

    await addToWallOfLove(page, "Grace Hopper");

    const snippet = await snippetFor(page, id);
    expect(snippet).toContain(`data-testimonial-space="${slug}"`);
    expect(snippet).toContain("/embed.js");

    const host = await renderEmbed(browser, snippet);

    await expect(host.locator(".tw-embed")).toHaveCount(1);
    await expect(host.getByText("Grace Hopper")).toBeVisible();
    await expect(host.getByText("It works, and I can say so.")).toBeVisible();

    // The email is never in the embed.
    await expect(host.getByText("grace@example.com")).toHaveCount(0);

    await host.context().close();
  });

  test("a testimonial that is not on the Wall of Love is not in the embed", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-not-loved"));

    const { slug, id } = await createSpace(page, {
      title: "Not Loved Reviews",
      slug: `not-loved-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Ada Lovelace",
      email: "ada@example.com",
      message: "Great, but not on the wall.",
    });

    const snippet = await snippetFor(page, id);

    const host = await renderEmbed(browser, snippet);

    // The embed loads and shows nothing, because nothing is on the Wall of
    // Love yet.
    await expect(host.locator(".tw-embed")).toHaveCount(1);
    await expect(host.getByText("Ada Lovelace")).toHaveCount(0);

    await host.context().close();
  });

  test("hiding a testimonial removes it from the embed", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-hidden"));

    const { slug, id } = await createSpace(page, {
      title: "Hidden Reviews",
      slug: `hidden-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "On the wall for now.",
    });

    await addToWallOfLove(page, "Grace Hopper");

    const snippet = await snippetFor(page, id);

    // Hide it from the inbox.
    await page.goto("/inbox");
    await hydrated(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Grace Hopper" });

    await row.getByRole("button", { name: "Hide", exact: true }).click();
    await expect(row.locator("span", { hasText: /^Hidden$/ })).toBeVisible();

    const host = await renderEmbed(browser, snippet);

    await expect(host.locator(".tw-embed")).toHaveCount(1);
    await expect(host.getByText("Grace Hopper")).toHaveCount(0);

    await host.context().close();
  });

  test("removing a testimonial from the Wall of Love removes it from the embed", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-removed"));

    const { slug, id } = await createSpace(page, {
      title: "Removed Reviews",
      slug: `removed-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "On the wall for now.",
    });

    await addToWallOfLove(page, "Grace Hopper");

    const snippet = await snippetFor(page, id);

    // Take it off the Wall of Love.
    await page.goto("/inbox");
    await hydrated(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Grace Hopper" });

    await row.getByRole("button", { name: "Remove from Wall of Love" }).click();
    await expect(
      row.locator("span", { hasText: /^Wall of Love$/ }),
    ).toHaveCount(0);

    const host = await renderEmbed(browser, snippet);

    await expect(host.locator(".tw-embed")).toHaveCount(1);
    await expect(host.getByText("Grace Hopper")).toHaveCount(0);

    await host.context().close();
  });

  test("deleting a testimonial removes it from the embed", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-deleted"));

    const { slug, id } = await createSpace(page, {
      title: "Deleted Reviews",
      slug: `deleted-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "About to be deleted.",
    });

    await addToWallOfLove(page, "Grace Hopper");

    const snippet = await snippetFor(page, id);

    // Delete it.
    await page.goto("/inbox");
    await hydrated(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Grace Hopper" });

    await row.getByRole("button", { name: "Delete" }).click();
    await row.getByRole("button", { name: "Yes, delete it" }).click();
    await expect(
      page.getByTestId("inbox-list").getByText("Grace Hopper"),
    ).toHaveCount(0);

    const host = await renderEmbed(browser, snippet);

    await expect(host.locator(".tw-embed")).toHaveCount(1);
    await expect(host.getByText("Grace Hopper")).toHaveCount(0);

    await host.context().close();
  });

  test("the embed for a deleted Space renders nothing and does not error the page", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-gone"));

    const { slug, id } = await createSpace(page, {
      title: "Gone Reviews",
      slug: `gone-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "This Space is about to go.",
    });

    await addToWallOfLove(page, "Grace Hopper");

    const snippet = await snippetFor(page, id);

    // Delete the Space itself.
    await page.goto(`/spaces/${id}`);
    await hydrated(page);
    await page.getByRole("button", { name: /delete/i }).click();
    await page.getByRole("button", { name: /yes, delete/i }).click();

    await expect(page).toHaveURL(/\/spaces$/);

    const context = await browser.newContext();
    const host = await context.newPage();

    // The host page must not error. A script error on the page would be a
    // failure of this test.
    const errors: string[] = [];
    host.on("pageerror", (error) => errors.push(error.message));

    await host.setContent(hostPage(snippet), { waitUntil: "domcontentloaded" });

    // Give the embed time to try and load, and to fail.
    await host.waitForTimeout(1500);

    await expect(host.getByRole("heading", { name: "Our customers" })).toBeVisible();
    await expect(host.locator(".tw-embed")).toHaveCount(0);
    expect(errors).toEqual([]);

    await context.close();
  });

  test("the premium options are locked for a Free user with an upgrade prompt", async ({
    page,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-locked"));

    const { id } = await createSpace(page, {
      title: "Locked Reviews",
      slug: `locked-reviews-${token}`,
    });

    await openEmbedConfig(page, id);

    // The carousel is shown, locked.
    const carousel = page.getByTestId("embed-choice-carousel");
    await expect(carousel).toBeDisabled();
    await expect(carousel).toContainText("Premium");
    await expect(carousel).toContainText("Upgrade to Premium");

    // The background colour and the animation are locked too.
    await expect(page.getByTestId("embed-locked")).toHaveCount(2);
  });

  test("a premium option is rejected by the backend for a Free user", async ({
    page,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-rejected"));

    const { id } = await createSpace(page, {
      title: "Rejected Reviews",
      slug: `rejected-reviews-${token}`,
    });

    // The UI is not the only thing that stands in the way: the API refuses
    // the premium settings too.
    const response = await page.request.put(`/api/spaces/${id}/embed`, {
      data: { layout: "carousel" },
    });

    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.errors.layout).toContain("Premium");
  });

  test("the preview updates when the options change", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-preview"));

    const { id } = await createSpace(page, {
      title: "Preview Reviews",
      slug: `preview-reviews-${token}`,
    });

    await openEmbedConfig(page, id);

    const preview = page.getByTestId("embed-preview");

    // Light by default.
    await expect(preview).toHaveClass(/tw-embed-light/);

    await page.getByTestId("embed-choice-dark").click();
    await expect(preview).toHaveClass(/tw-embed-dark/);

    // Saving keeps the choice.
    await page.getByRole("button", { name: "Save embed" }).click();
    await expect(page.getByTestId("embed-saved")).toBeVisible();

    await page.reload();
    await expect(page.getByTestId("embed-preview")).toHaveClass(/tw-embed-dark/);
  });

  test("the snippet names the Space and points at the script", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-snippet"));

    const { slug, id } = await createSpace(page, {
      title: "Snippet Reviews",
      slug: `snippet-reviews-${token}`,
    });

    const snippet = await snippetFor(page, id);

    expect(snippet).toContain(`<div data-testimonial-space="${slug}"></div>`);
    expect(snippet).toContain('<script src="');
    expect(snippet).toContain("/embed.js");
  });

  test("markup in a testimonial is shown as text, not as markup", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("embed-xss"));

    const { slug, id } = await createSpace(page, {
      title: "Xss Reviews",
      slug: `embed-xss-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Ada",
      email: "ada@example.com",
      message: "<script>window.__pwned = true;</script>",
    });

    await addToWallOfLove(page, "Ada");

    const snippet = await snippetFor(page, id);

    const context = await browser.newContext();
    const host = await context.newPage();

    await host.setContent(hostPage(snippet), { waitUntil: "domcontentloaded" });

    await expect(host.getByText("Ada")).toBeVisible();

    // The script did not run.
    const pwned = await host.evaluate(() => (window as never as {
      __pwned?: boolean;
    }).__pwned);
    expect(pwned).toBeUndefined();

    // The markup is on the page as text.
    await expect(host.locator(".tw-embed-card-message")).toHaveText(
      "<script>window.__pwned = true;</script>",
    );

    await context.close();
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
