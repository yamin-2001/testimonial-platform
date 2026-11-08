import { expect, test } from "@playwright/test";

/**
 * The Spaces flow (PRD 4.2, 4.3, 4.4).
 *
 * These tests drive the real UI against the real backend: they register an
 * account, create a Space through the wizard, and check what the owner sees.
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
 * Registers an account, confirms its email address and signs in.
 *
 * The backend sends the verification link by email, which the test cannot
 * read. Instead the account is confirmed directly in the database, which is
 * what the emailed link does anyway.
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

  // Registration does not sign the user in, so sign in now.
  await page.goto("/login");
  await hydrated(page);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("a-good-password");
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
}

/**
 * Waits for React to attach its event handlers.
 *
 * A click that lands before hydration is handled by the browser rather than
 * by React, which would submit the form the wrong way.
 */
async function hydrated(page: import("@playwright/test").Page) {
  await page.waitForLoadState("networkidle");
  await page.waitForFunction(() => {
    // React marks a hydrated root with the event handlers it attached.
    const root = document.querySelector("form");
    return root !== null && Object.keys(root).some((key) =>
      key.startsWith("__reactProps$"),
    );
  });
}

/**
 * Walks the wizard to the last step and submits.
 *
 * The steps are: details, fields, theme, ratings (PRD 4.2).
 */
async function fillWizard(
  page: import("@playwright/test").Page,
  { title, slug }: { title: string; slug?: string },
) {
  await page.getByLabel("Title", { exact: true }).fill(title);
  if (slug) {
    await page.getByLabel("Public URL").fill(slug);
  }
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
}

/**
 * Submits the wizard.
 *
 * The wizard navigates away as soon as the Space is saved, so the click is
 * not allowed to wait for the page to settle on an element that is about to
 * disappear.
 */
async function submitWizard(page: import("@playwright/test").Page) {
  await page
    .getByRole("button", { name: /create space/i })
    .click({ noWaitAfter: true });
}

test.describe("the Spaces wizard", () => {
  test("a new Space is created and its public URL is shown", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("spaces"));

    await page.goto("/spaces/new");
    await expect(
      page.getByRole("heading", { name: "New Space" }),
    ).toBeVisible();

    await fillWizard(page, {
      title: "Acme Reviews",
      slug: `acme-reviews-${token}`,
    });

    // The theme step is where the preview is chosen.
    await page.getByRole("button", { name: "3. Theme" }).click();
    await page.getByRole("button", { name: "Dark" }).click();
    await page.getByRole("button", { name: "4. Ratings" }).click();

    await submitWizard(page);

    // The success page shows the public URL (PRD 4.2).
    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    await expect(page.getByLabel("Public URL")).toHaveValue(
      new RegExp(`/s/acme-reviews-${token}$`),
    );
  });

  test("a duplicate slug is rejected with an error on the field", async ({
    page,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("duplicate"));

    // Take the slug first.
    await page.goto("/spaces/new");
    await fillWizard(page, {
      title: "First Space",
      slug: `first-space-${token}`,
    });
    await submitWizard(page);
    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    // Now try to take it again, in a different Space.
    await page.goto("/spaces/new");
    await fillWizard(page, {
      title: "Second Space",
      slug: `first-space-${token}`,
    });
    await submitWizard(page);

    // The error lands on the slug field (PRD 4.2).
    await expect(page.getByText(/already taken/i)).toBeVisible();
    await expect(page.getByRole("heading", { name: "New Space" })).toBeVisible();
  });

  test("a Space shows in the list and can be deleted", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("delete"));

    await page.goto("/spaces/new");
    await fillWizard(page, {
      title: "Doomed Space",
      slug: `doomed-space-${token}`,
    });
    await submitWizard(page);
    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    await page.goto("/spaces");
    await expect(
      page.getByRole("heading", { name: "Doomed Space" }),
    ).toBeVisible();

    // Deleting needs a confirmation (PRD 4.2).
    await page.getByRole("link", { name: "Doomed Space" }).click();
    await page.getByRole("button", { name: "Delete" }).click();
    await expect(page.getByText(/cannot be undone/i)).toBeVisible();
    await page.getByRole("button", { name: /yes, delete it/i }).click();

    await expect(page).toHaveURL(/\/spaces$/);
    await expect(page.getByRole("heading", { name: "Doomed Space" })).toHaveCount(
      0,
    );
  });

  test("a Space can be edited and the change is kept", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("edit"));

    await page.goto("/spaces/new");
    await fillWizard(page, {
      title: "Original Title",
      slug: `editable-space-${token}`,
    });
    await submitWizard(page);
    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    await page.getByRole("link", { name: "View the Space" }).click();
    await expect(
      page.getByRole("heading", { name: "Original Title" }),
    ).toBeVisible();

    await page.getByRole("link", { name: "Edit" }).click();
    await expect(
      page.getByRole("heading", { name: /edit .*original title/i }),
    ).toBeVisible();

    await page.getByLabel("Title", { exact: true }).fill("Renamed Title");
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: /save changes/i }).click();

    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    await page.goto("/spaces");
    await expect(page.getByRole("heading", { name: "Renamed Title" })).toBeVisible();
  });

  test("a premium theme is locked for a Free user", async ({ page }) => {
    await registerAndVerify(page, uniqueEmail("premium"));

    await page.goto("/spaces/new");
    await page.getByRole("button", { name: "3. Theme" }).click();

    // The premium themes are shown but cannot be chosen (PRD 4.4).
    const midnight = page.getByRole("button", { name: /midnight/i });
    await expect(midnight).toBeDisabled();
    await expect(page.getByText(/upgrade to premium/i).first()).toBeVisible();
  });

  test("the Free plan allows three Spaces and rejects the fourth", async ({
    page,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("limit"));

    for (let i = 1; i <= 3; i++) {
      await page.goto("/spaces/new");
      await fillWizard(page, {
        title: `Limit Space ${i}`,
        slug: `limit-space-${i}-${token}`,
      });
      await submitWizard(page);
      await expect(
        page.getByRole("heading", { name: /your space is ready/i }),
      ).toBeVisible();
    }

    // The fourth is over the limit (PRD 4.2).
    await page.goto("/spaces/new");
    await fillWizard(page, {
      title: "Limit Space 4",
      slug: `limit-space-4-${token}`,
    });
    await submitWizard(page);

    await expect(page.getByText(/3 spaces/i)).toBeVisible();
  });

  test("the dashboard shows the user's own numbers", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("dashboard"));
    await page.goto("/spaces/new");
    await fillWizard(page, {
      title: "Counted Space",
      slug: `counted-space-${token}`,
    });
    await submitWizard(page);
    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    await page.goto("/dashboard");
    await expect(page.getByRole("term").filter({ hasText: "Spaces" })).toBeVisible();
    await expect(page.getByText("Counted Space")).toBeVisible();
  });

  test("an unverified account is asked to confirm its email address", async ({
    page,
  }) => {
    const email = uniqueEmail("unverified");

    await page.goto("/register");
    await page.getByLabel("Name").fill("Unverified Owner");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password").fill("a-good-password");
    await page.getByRole("button", { name: /create account/i }).click();

    await expect(
      page.getByRole("heading", { name: /check your email/i }),
    ).toBeVisible();

    await page.goto("/login");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password").fill("a-good-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page).toHaveURL(/\/dashboard$/);

    // The Space creation page asks for a verified address (PRD 4.1).
    await page.goto("/spaces/new");
    await expect(
      page.getByRole("heading", { name: /confirm your email address/i }),
    ).toBeVisible();
  });

  test("a signed-out visitor is sent to the login page", async ({ page }) => {
    await page.goto("/spaces");
    await expect(page).toHaveURL(/\/login\?target=/);
  });
});

/**
 * The live preview in the wizard (PRD 4.3, 4.4).
 *
 * The preview shows what the customer will see, so it must follow the
 * choices the owner makes.
 */
test.describe("the wizard's preview", () => {
  test("the preview shows the fields the Space collects", async ({ page }) => {
    await registerAndVerify(page, uniqueEmail("preview"));

    await page.goto("/spaces/new");
    await page.getByLabel("Title", { exact: true }).fill("Preview Space");
    await page.getByRole("button", { name: "Continue" }).click();

    await expect(
      page.getByRole("heading", { name: /what should the form ask for/i }),
    ).toBeVisible();

    // The preview shows every optional field by default. The wizard's own
    // inputs are in the form too, so the preview is scoped by its hook.
    const preview = page.getByTestId("testimonial-form");

    // Name, email, the four optional fields and the testimonial.
    for (const name of [
      "name",
      "email",
      "companyName",
      "jobTitle",
      "websiteUrl",
      "socialLinks",
      "message",
    ]) {
      await expect(preview.locator(`[name="${name}"]`)).toHaveCount(1);
    }

    // Turning one off removes it from the preview.
    await page.getByRole("checkbox", { name: "Collect Company name" }).uncheck();
    await expect(preview.locator('[name="companyName"]')).toHaveCount(0);
    await expect(preview.locator('[name="jobTitle"]')).toHaveCount(1);
  });

  test("a field switched off is stored off and stays off", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("field-round-trip"));

    await page.goto("/spaces/new");
    await page.getByLabel("Title", { exact: true }).fill("Round Trip");
    await page.getByLabel("Public URL").fill(`round-trip-${token}`);
    await page.getByRole("button", { name: "Continue" }).click();

    // Switch the company name off.
    await page.getByRole("checkbox", { name: "Collect Company name" }).uncheck();
    await expect(
      page.getByTestId("testimonial-form").locator('[name="companyName"]'),
    ).toHaveCount(0);

    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: "Continue" }).click();
    await submitWizard(page);

    await expect(
      page.getByRole("heading", { name: /your space is ready/i }),
    ).toBeVisible();

    // The backend stored it as off, and the Space's page says so.
    await page.goto("/spaces");
    await page.getByRole("link", { name: "Round Trip" }).click();

    const companyRow = page.locator("li", { hasText: "Company name" });
    await expect(companyRow).toContainText("Not collected");

    // The other optional fields are still collected.
    const jobRow = page.locator("li", { hasText: "Job title" });
    await expect(jobRow).toContainText("Collected, optional");
  });

  test("the preview follows the chosen theme", async ({ page }) => {
    await registerAndVerify(page, uniqueEmail("theme-preview"));

    await page.goto("/spaces/new");
    await page.getByLabel("Title", { exact: true }).fill("Themed Space");
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: "Continue" }).click();

    await expect(
      page.getByRole("heading", { name: /choose a theme/i }),
    ).toBeVisible();

    // The preview's own background is the theme's background.
    const preview = page.getByTestId("testimonial-form");
    const light = await preview.evaluate(
      (el) => getComputedStyle(el).backgroundColor,
    );

    await page.getByRole("button", { name: "Dark" }).click();
    await page.waitForTimeout(200);

    const dark = await preview.evaluate(
      (el) => getComputedStyle(el).backgroundColor,
    );

    expect(light).not.toEqual("rgba(0, 0, 0, 0)");
    expect(dark).not.toEqual(light);
  });

  test("the preview shows the rating once it is switched on", async ({ page }) => {
    await registerAndVerify(page, uniqueEmail("rating-preview"));

    await page.goto("/spaces/new");
    await page.getByLabel("Title", { exact: true }).fill("Rated Space");
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("button", { name: "Continue" }).click();

    await expect(
      page.getByRole("heading", { name: /ask for a rating/i }),
    ).toBeVisible();

    // No stars yet.
    await expect(page.getByRole("img", { name: "1 star" })).toHaveCount(0);

    await page.getByRole("checkbox", { name: /show a 1.5 star rating/i }).check();
    await expect(page.getByRole("img", { name: "1 star" })).toHaveCount(1);
    await expect(page.getByRole("img", { name: "5 stars" })).toHaveCount(1);
  });
});

/**
 * Confirms an account's email address directly in the database.
 *
 * The verification link is emailed, and the test has no access to the inbox.
 * This does exactly what the link does.
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
