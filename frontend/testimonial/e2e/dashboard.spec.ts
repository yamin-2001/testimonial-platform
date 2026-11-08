import { expect, test } from "@playwright/test";

/**
 * The dashboard (PRD 4.6).
 *
 * These tests drive the real page against the real backend. They create a
 * Space, send it real submissions through the public form, and then check
 * that the dashboard's numbers and its chart match what actually happened --
 * the acceptance criterion in PRD 4.6 is that the numbers reflect only the
 * user's own data, so the tests check what the API returns too, not just what
 * the page shows.
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
 */
async function hydrated(page: import("@playwright/test").Page) {
  await page.waitForLoadState("networkidle");
  await page.waitForFunction(() => {
    const root = document.querySelector("form") ?? document.body;
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
 * Creates a Space through the wizard.
 *
 * @returns the Space's slug.
 */
async function createSpace(
  page: import("@playwright/test").Page,
  title: string,
): Promise<string> {
  const slug = `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${runToken()}`;

  await page.goto("/spaces/new");
  await hydrated(page);

  await page.getByLabel("Title", { exact: true }).fill(title);
  await page.getByLabel("Public URL").fill(slug);
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(
    page.getByRole("heading", { name: /what should the form ask for/i }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(
    page.getByRole("heading", { name: /choose a theme/i }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(
    page.getByRole("heading", { name: /ask for a rating/i }),
  ).toBeVisible();

  await page.getByRole("button", { name: /create space/i }).click({
    noWaitAfter: true,
  });

  await expect(
    page.getByRole("heading", { name: /your space is ready/i }),
  ).toBeVisible();

  return slug;
}

/**
 * Sends a testimonial to a Space's public form, as a visitor would.
 */
async function submit(
  page: import("@playwright/test").Page,
  slug: string,
  name: string,
  email: string,
  message: string,
) {
  await page.goto(`/s/${slug}`);
  await hydrated(page);

  await page.getByLabel("Your name").fill(name);
  await page.getByLabel("Your email").fill(email);
  await page.getByLabel("Your testimonial").fill(message);
  await page.getByRole("button", { name: "Send testimonial" }).click();

  await expect(page).toHaveURL(new RegExp(`/s/${slug}/thank-you$`));
}

/**
 * Opens the dashboard and waits for it to arrive.
 */
async function openDashboard(page: import("@playwright/test").Page) {
  await page.goto("/dashboard");
  await hydrated(page);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
}

/**
 * The dashboard's analytics, as the API returns them.
 */
async function analyticsOf(
  page: import("@playwright/test").Page,
): Promise<{
  range: number;
  series: { date: string; count: number }[];
  recent: { name: string; message: string }[];
}> {
  return page.evaluate(async () => {
    const response = await fetch("/api/analytics/dashboard");
    return response.json();
  });
}

test.describe("the dashboard", () => {
  test("a new account sees the Free plan notice and no submissions", async ({
    page,
  }) => {
    await registerAndVerify(page, uniqueEmail("dash-empty"));

    await openDashboard(page);

    // A Free user is told what the Free plan allows and that Premium exists
    // (PRD 4.6).
    await expect(page.getByTestId("dashboard-free-notice")).toBeVisible();

    // The headline numbers are all zero.
    await expect(page.getByTestId("stat-spaces")).toContainText("0");
    await expect(page.getByTestId("stat-testimonials")).toContainText("0");
    await expect(page.getByTestId("stat-unique-submitters")).toContainText("0");

    // And there is nothing to chart, said plainly rather than drawn as a
    // flat line.
    await expect(page.getByTestId("chart-empty")).toBeVisible();
    await expect(page.getByTestId("submissions-chart")).toHaveCount(0);

    // The recent-submissions list says so too.
    await expect(page.getByText("No submissions yet")).toBeVisible();
  });

  test("the numbers match the submissions that were actually received", async ({
    page,
    browser,
  }) => {
    await registerAndVerify(page, uniqueEmail("dash-counts"));

    const slug = await createSpace(page, "Acme Reviews");

    // Two visitors send a testimonial, one of them twice.
    const visitor = await browser.newPage();
    await submit(visitor, slug, "Ada", "ada@example.com", "It was lovely.");
    await submit(visitor, slug, "Grace", "grace@example.com", "Wonderful.");
    await submit(visitor, slug, "Ada", "ada@example.com", "Again.");
    await visitor.close();

    await openDashboard(page);

    // One Space, three testimonials, two distinct submitters (PRD 4.6).
    await expect(page.getByTestId("stat-spaces")).toContainText("1");
    await expect(page.getByTestId("stat-testimonials")).toContainText("3");
    await expect(page.getByTestId("stat-unique-submitters")).toContainText("2");

    // The chart is drawn, and says how many submissions it covers.
    const chart = page.getByTestId("submissions-chart");
    await expect(chart).toBeVisible();
    await expect(chart).toHaveAttribute(
      "aria-label",
      /Submissions per day\. 3 in total over 30 days\./,
    );

    // The recent list names the submitters.
    const recent = page.getByTestId("recent-submissions");
    await expect(recent).toBeVisible();
    await expect(recent.getByText("Ada").first()).toBeVisible();
    await expect(recent.getByText("Grace").first()).toBeVisible();
    await expect(recent.getByText("It was lovely.")).toBeVisible();

    // The API agrees with the page -- the claim is not just what the page
    // shows.
    const analytics = await analyticsOf(page);
    expect(analytics.range).toBe(30);
    expect(analytics.series).toHaveLength(30);
    expect(analytics.series.reduce((sum, day) => sum + day.count, 0)).toBe(3);
    expect(analytics.recent).toHaveLength(3);
  });

  test("the days with no submissions are counted as zero", async ({
    page,
    browser,
  }) => {
    await registerAndVerify(page, uniqueEmail("dash-zeros"));

    const slug = await createSpace(page, "Acme Reviews");

    const visitor = await browser.newPage();
    await submit(visitor, slug, "Ada", "ada@example.com", "It was lovely.");
    await visitor.close();

    await openDashboard(page);

    // Thirty days, one of them with a submission and the other twenty-nine
    // with none (PRD 4.6).
    const analytics = await analyticsOf(page);
    expect(analytics.series).toHaveLength(30);
    expect(analytics.series.filter((day) => day.count === 0)).toHaveLength(29);
    expect(analytics.series.filter((day) => day.count === 1)).toHaveLength(1);
  });

  test("another owner's submissions are not counted", async ({
    page,
    browser,
  }) => {
    await registerAndVerify(page, uniqueEmail("dash-mine"));

    const slug = await createSpace(page, "Acme Reviews");

    const visitor = await browser.newPage();
    await visitor.context().clearCookies();
    await submit(visitor, slug, "Mine", "mine@example.com", "Mine only.");
    await visitor.close();

    // A different account, with a submission of its own.
    const other = await browser.newPage();
    await registerAndVerify(other, uniqueEmail("dash-theirs"));
    const otherSlug = await createSpace(other, "Other Reviews");
    const otherVisitor = await browser.newPage();
    await otherVisitor.context().clearCookies();
    await submit(
      otherVisitor,
      otherSlug,
      "Theirs",
      "theirs@example.com",
      "Not mine.",
    );
    await otherVisitor.close();
    await other.close();

    await openDashboard(page);

    // Only this owner's data is counted (PRD 4.6).
    await expect(page.getByTestId("stat-spaces")).toContainText("1");
    await expect(page.getByTestId("stat-testimonials")).toContainText("1");
    await expect(page.getByTestId("stat-unique-submitters")).toContainText("1");

    await expect(
      page.getByTestId("recent-submissions").getByText("Mine only."),
    ).toBeVisible();
    await expect(
      page.getByTestId("recent-submissions").getByText("Not mine."),
    ).toHaveCount(0);

    // And the API agrees.
    const analytics = await analyticsOf(page);
    expect(analytics.recent.map((item) => item.name)).toEqual(["Mine"]);
  });

  test("the chart's range can be chosen", async ({ page, browser }) => {
    await registerAndVerify(page, uniqueEmail("dash-range"));

    const slug = await createSpace(page, "Acme Reviews");

    const visitor = await browser.newPage();
    await visitor.context().clearCookies();
    await submit(visitor, slug, "Ada", "ada@example.com", "It was lovely.");
    await visitor.close();

    await openDashboard(page);

    // The default is thirty days (PRD 4.6).
    await expect(page.getByTestId("chart-range-30")).toBeChecked();
    await expect(page.getByTestId("chart-range-summary")).toContainText(
      "30 days",
    );

    // Choosing seven days refetches the series for that range. The radio
    // itself is visually hidden, so the label is what a user clicks.
    await page.getByTestId("chart-range-picker").getByText("7 days").click();
    await expect(page.getByTestId("chart-range-summary")).toContainText("7 days");
    await expect(page.getByTestId("chart-range-7")).toBeChecked();

    // The submission is still in the shorter range -- it happened today.
    await expect(page.getByTestId("submissions-chart")).toHaveAttribute(
      "aria-label",
      /1 in total over 7 days\./,
    );

    // And ninety days works too.
    await page.getByTestId("chart-range-picker").getByText("90 days").click();
    await expect(page.getByTestId("chart-range-summary")).toContainText("90 days");
    await expect(page.getByTestId("submissions-chart")).toHaveAttribute(
      "aria-label",
      /1 in total over 90 days\./,
    );
  });

  test("a deleted testimonial is excluded from the counts", async ({
    page,
    browser,
  }) => {
    await registerAndVerify(page, uniqueEmail("dash-deleted"));

    const slug = await createSpace(page, "Acme Reviews");

    const visitor = await browser.newPage();
    await visitor.context().clearCookies();
    await submit(visitor, slug, "Ada", "ada@example.com", "It was lovely.");
    await visitor.close();

    await openDashboard(page);
    await expect(page.getByTestId("stat-testimonials")).toContainText("1");

    // Delete it from the Space's inbox.
    const spaceId = await spaceIdOf(page, "Acme Reviews");
    await page.goto(`/inbox?space=${spaceId}`);
    await hydrated(page);

    await expect(page.getByTestId("inbox-list")).toBeVisible();

    const row = page.getByTestId(/^testimonial-[0-9a-f-]{36}$/).first();
    await expect(row).toBeVisible();

    await row.getByRole("button", { name: "Delete" }).click();
    await page.getByRole("button", { name: "Yes, delete it" }).click();

    await expect(page.getByTestId("inbox-list")).toHaveCount(0);

    await openDashboard(page);

    // The count is back to zero, and the chart says so (PRD 4.6).
    await expect(page.getByTestId("stat-testimonials")).toContainText("0");
    await expect(page.getByTestId("stat-unique-submitters")).toContainText("0");
    await expect(page.getByTestId("chart-empty")).toBeVisible();
  });

  test("a signed-out visitor is sent to the login page", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login\?target=/);
  });
});

/**
 * The id of one of the signed-in user's Spaces.
 */
async function spaceIdOf(
  page: import("@playwright/test").Page,
  title: string,
): Promise<string> {
  const spaces = await page.evaluate(async () => {
    const response = await fetch("/api/spaces");
    return (await response.json()) as { id: string; title: string }[];
  });

  const space = spaces.find((candidate) => candidate.title === title);
  expect(space, `no Space titled "${title}"`).toBeTruthy();

  return space!.id;
}

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
