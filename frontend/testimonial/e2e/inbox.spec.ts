import { expect, test } from "@playwright/test";

/**
 * The owner's inbox (PRD 4.7).
 *
 * These tests drive the real inbox against the real backend: a visitor sends
 * a testimonial, the owner sees it, and the owner acts on it.
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
  options: { title: string; slug: string; ratingEnabled?: boolean },
): Promise<string> {
  await page.goto("/spaces/new");
  await hydrated(page);
  await page.getByLabel("Title", { exact: true }).fill(options.title);
  await page.getByLabel("Public URL").fill(options.slug);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Continue" }).click();

  if (options.ratingEnabled) {
    await page.getByRole("checkbox", { name: /show a 1.5 star rating/i }).check();
  }

  await page
    .getByRole("button", { name: /create space/i })
    .click({ noWaitAfter: true });

  await expect(
    page.getByRole("heading", { name: /your space is ready/i }),
  ).toBeVisible();

  return options.slug;
}

/**
 * Sends a testimonial to a Space as a visitor, in a browser of its own.
 *
 * The visitor is a different person from the owner, so it gets its own
 * context and the owner stays signed in.
 */
async function submitTestimonial(
  browser: import("@playwright/test").Browser,
  slug: string,
  details: { name: string; email: string; message: string; rating?: number },
) {
  const visitor = await browser.newContext();
  const visitorPage = await visitor.newPage();

  await visitorPage.goto(`/s/${slug}`);
  await hydrated(visitorPage);
  await visitorPage.getByLabel("Your name").fill(details.name);
  await visitorPage.getByLabel("Your email").fill(details.email);
  await visitorPage.getByLabel("Your testimonial").fill(details.message);

  if (details.rating !== undefined) {
    await visitorPage
      .getByRole("radio", { name: `${details.rating} star${details.rating === 1 ? "" : "s"}` })
      .check();
  }

  await visitorPage
    .getByRole("button", { name: "Send testimonial" })
    .click();

  await expect(
    visitorPage.getByRole("heading", { name: /thank you/i }),
  ).toBeVisible();

  await visitor.close();
}

/**
 * Opens the owner's inbox and waits for the list to arrive.
 */
async function openInbox(page: import("@playwright/test").Page) {
  await page.goto("/inbox");
  await hydrated(page);
  await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();
}

test.describe("the inbox", () => {
  test("a submitted testimonial appears in the owner's inbox", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("inbox-list"));

    const slug = await createSpace(page, {
      title: "Inbox Reviews",
      slug: `inbox-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "It works, and I can say so.",
    });

    await openInbox(page);

    await expect(page.getByTestId("inbox-list")).toBeVisible();
    await expect(page.getByText("Grace Hopper")).toBeVisible();
    await expect(page.getByText("grace@example.com")).toBeVisible();
    await expect(page.getByText("It works, and I can say so.")).toBeVisible();
  });

  test("a new testimonial is visible and not on the Wall of Love", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("defaults"));

    const slug = await createSpace(page, {
      title: "Defaults Reviews",
      slug: `defaults-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Ada Lovelace",
      email: "ada@example.com",
      message: "Great.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Ada Lovelace" });

    // There is no approval step: the testimonial is visible right away
    // (PRD 4.7). The badges are the markers, so they are matched exactly:
    // the buttons' own labels contain the same words.
    await expect(row.locator("span", { hasText: /^Hidden$/ })).toHaveCount(0);
    await expect(
      row.locator("span", { hasText: /^Wall of Love$/ }),
    ).toHaveCount(0);

    // The favourite and Wall of Love buttons are not pressed.
    await expect(row.getByRole("button", { name: "Favourite" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    await expect(
      row.getByRole("button", { name: "Add to Wall of Love" }),
    ).toHaveAttribute("aria-pressed", "false");
  });

  test("a favourite sorts above the others", async ({ page, browser }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("favourite-sort"));

    const slug = await createSpace(page, {
      title: "Sort Reviews",
      slug: `sort-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "First One",
      email: "first@example.com",
      message: "Submitted first.",
    });
    await submitTestimonial(browser, slug, {
      name: "Second One",
      email: "second@example.com",
      message: "Submitted second.",
    });

    await openInbox(page);

    // The newest is first to begin with.
    await expect(
      page.getByTestId("inbox-list").getByRole("listitem").first(),
    ).toContainText("Second One");

    // Favouriting the older one brings it to the top (PRD 4.7).
    const older = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "First One" });

    await older.getByRole("button", { name: "Favourite" }).click();
    await expect(
      older.getByRole("button", { name: "Unfavourite" }),
    ).toHaveAttribute("aria-pressed", "true");

    await expect(
      page.getByTestId("inbox-list").getByRole("listitem").first(),
    ).toContainText("First One");
  });

  test("hiding marks the testimonial hidden and keeps it in the inbox", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("hide"));

    const slug = await createSpace(page, {
      title: "Hide Reviews",
      slug: `hide-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Hidden One",
      email: "hidden@example.com",
      message: "Not for the public.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Hidden One" });

    await row.getByRole("button", { name: "Hide" }).click();

    await expect(row.locator("span", { hasText: /^Hidden$/ })).toBeVisible();
    await expect(row.getByRole("button", { name: "Show" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    // It stays in the inbox, marked hidden (PRD 4.7).
    await expect(row).toBeVisible();
  });

  test("a hidden testimonial is still found by the hidden filter", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("hidden-filter"));

    const slug = await createSpace(page, {
      title: "Filter Reviews",
      slug: `filter-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Visible One",
      email: "visible@example.com",
      message: "Out in the open.",
    });
    await submitTestimonial(browser, slug, {
      name: "Hidden One",
      email: "hidden@example.com",
      message: "Out of sight.",
    });

    await openInbox(page);

    const hiddenRow = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Hidden One" });
    await hiddenRow.getByRole("button", { name: "Hide" }).click();
    await expect(
      hiddenRow.locator("span", { hasText: /^Hidden$/ }),
    ).toBeVisible();

    // The filter narrows the list to the hidden ones only.
    await page.getByTestId("filter-hidden").check();

    await expect(
      page.getByTestId("inbox-list").getByRole("listitem"),
    ).toHaveCount(1);
    await expect(page.getByText("Hidden One")).toBeVisible();
    await expect(page.getByText("Visible One")).toHaveCount(0);
  });

  test("editing changes the testimonial's text", async ({ page, browser }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("edit"));

    const slug = await createSpace(page, {
      title: "Edit Reviews",
      slug: `edit-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Ada Lovelace",
      email: "ada@example.com",
      message: "The original text.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Ada Lovelace" });

    await row.getByRole("button", { name: "Edit" }).click();

    const form = row.getByRole("textbox", { name: "Testimonial" });
    await form.fill("The edited text.");
    await row.getByRole("button", { name: "Save changes" }).click();

    await expect(page.getByText("The edited text.")).toBeVisible();
    await expect(page.getByText("The original text.")).toHaveCount(0);

    // The change is kept, not just shown (PRD 4.7).
    await page.reload();
    await expect(page.getByText("The edited text.")).toBeVisible();
  });

  test("an edit that empties the text is refused with a field error", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("edit-empty"));

    const slug = await createSpace(page, {
      title: "Empty Reviews",
      slug: `empty-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Ada Lovelace",
      email: "ada@example.com",
      message: "The original text.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Ada Lovelace" });

    await row.getByRole("button", { name: "Edit" }).click();
    await row.getByRole("textbox", { name: "Testimonial" }).fill("   ");
    await row.getByRole("button", { name: "Save changes" }).click();

    // The backend's field error is shown on the form.
    await expect(row.getByText(/testimonial.*required/i)).toBeVisible();
    await expect(page.getByText("The original text.")).toBeVisible();
  });

  test("deleting removes the testimonial after a confirmation", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("delete"));

    const slug = await createSpace(page, {
      title: "Delete Reviews",
      slug: `delete-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Doomed One",
      email: "doomed@example.com",
      message: "About to go.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Doomed One" });

    // The delete asks for a confirmation (PRD 4.7).
    await row.getByRole("button", { name: "Delete" }).click();
    await expect(page.getByText(/cannot be undone/i)).toBeVisible();

    // Nothing is gone yet.
    await expect(page.getByText("Doomed One")).toBeVisible();

    await page.getByRole("button", { name: /yes, delete it/i }).click();

    await expect(page.getByText("Doomed One")).toHaveCount(0);
  });

  test("cancelling the delete confirmation keeps the testimonial", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("delete-cancel"));

    const slug = await createSpace(page, {
      title: "Cancel Reviews",
      slug: `cancel-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Kept One",
      email: "kept@example.com",
      message: "Staying put.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Kept One" });

    await row.getByRole("button", { name: "Delete" }).click();
    await page.getByRole("button", { name: "Cancel" }).click();

    await expect(page.getByText("Kept One")).toBeVisible();
    await expect(page.getByText(/cannot be undone/i)).toHaveCount(0);
  });

  test("the search finds a testimonial by name, email or text", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("search"));

    const slug = await createSpace(page, {
      title: "Search Reviews",
      slug: `search-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Grace Hopper",
      email: "grace@example.com",
      message: "Compilers are fun.",
    });
    await submitTestimonial(browser, slug, {
      name: "Ada Lovelace",
      email: "ada@example.com",
      message: "Analytical engines are neat.",
    });

    await openInbox(page);

    const list = page.getByTestId("inbox-list").getByRole("listitem");
    await expect(list).toHaveCount(2);

    // By name.
    await page.getByLabel("Search").fill("grace");
    await page.getByRole("button", { name: "Search" }).click();
    await expect(list).toHaveCount(1);
    await expect(page.getByText("Grace Hopper")).toBeVisible();

    // By email.
    await page.getByLabel("Search").fill("ada@example.com");
    await page.getByRole("button", { name: "Search" }).click();
    await expect(list).toHaveCount(1);
    await expect(page.getByText("Ada Lovelace")).toBeVisible();

    // By text.
    await page.getByLabel("Search").fill("analytical");
    await page.getByRole("button", { name: "Search" }).click();
    await expect(list).toHaveCount(1);
    await expect(page.getByText("Ada Lovelace")).toBeVisible();
  });

  test("the favourite filter keeps only the favourites", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("favourite-filter"));

    const slug = await createSpace(page, {
      title: "Favourite Reviews",
      slug: `favourite-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Favourite One",
      email: "fav@example.com",
      message: "The good one.",
    });
    await submitTestimonial(browser, slug, {
      name: "Ordinary One",
      email: "ordinary@example.com",
      message: "The other one.",
    });

    await openInbox(page);

    const favourite = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Favourite One" });

    await favourite.getByRole("button", { name: "Favourite" }).click();
    await expect(
      favourite.getByRole("button", { name: "Unfavourite" }),
    ).toHaveAttribute("aria-pressed", "true");

    await page.getByTestId("filter-favorite").check();

    const list = page.getByTestId("inbox-list").getByRole("listitem");
    await expect(list).toHaveCount(1);
    await expect(page.getByText("Favourite One")).toBeVisible();
    await expect(page.getByText("Ordinary One")).toHaveCount(0);
  });

  test("the rating filter keeps only the chosen rating", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("rating-filter"));

    const slug = await createSpace(page, {
      title: "Rating Filter Reviews",
      slug: `rating-filter-reviews-${token}`,
      ratingEnabled: true,
    });

    await submitTestimonial(browser, slug, {
      name: "Five Star",
      email: "five@example.com",
      message: "Excellent.",
      rating: 5,
    });
    await submitTestimonial(browser, slug, {
      name: "Three Star",
      email: "three@example.com",
      message: "Fine.",
      rating: 3,
    });

    await openInbox(page);

    await page.getByTestId("filter-rating").selectOption("5");

    const list = page.getByTestId("inbox-list").getByRole("listitem");
    await expect(list).toHaveCount(1);
    await expect(page.getByText("Five Star")).toBeVisible();
    await expect(page.getByText("Three Star")).toHaveCount(0);
  });

  test("adding to the Wall of Love is the owner's own action", async ({
    page,
    browser,
  }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("wall-of-love"));

    const slug = await createSpace(page, {
      title: "Love Reviews",
      slug: `love-reviews-${token}`,
    });

    await submitTestimonial(browser, slug, {
      name: "Loved One",
      email: "loved@example.com",
      message: "I love it.",
    });

    await openInbox(page);

    const row = page
      .getByTestId("inbox-list")
      .getByRole("listitem")
      .filter({ hasText: "Loved One" });

    // Nothing happens without a click (PRD 4.7). The badge is the marker
    // that says it is on the Wall of Love; the button's own label contains
    // the same words.
    const badge = row.locator("span", { hasText: /^Wall of Love$/ });
    await expect(badge).toHaveCount(0);

    await row.getByRole("button", { name: "Add to Wall of Love" }).click();

    await expect(badge).toBeVisible();
    await expect(
      row.getByRole("button", { name: "Remove from Wall of Love" }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  test("the Space can be switched from the inbox", async ({ page }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("switch"));

    await createSpace(page, {
      title: "First Reviews",
      slug: `first-reviews-${token}`,
    });
    await createSpace(page, {
      title: "Second Reviews",
      slug: `second-reviews-${token}`,
    });

    await openInbox(page);

    const picker = page.getByTestId("inbox-space");
    await expect(picker).toHaveCount(1);

    const options = await picker.locator("option").allTextContents();
    expect(options).toEqual(
      expect.arrayContaining(["First Reviews", "Second Reviews"]),
    );
  });

  test("another owner's Space is not reachable", async ({ page, browser }) => {
    const token = runToken();
    await registerAndVerify(page, uniqueEmail("isolation"));

    await createSpace(page, {
      title: "Private Reviews",
      slug: `private-reviews-${token}`,
    });

    // The first owner's Space id, which the second owner must not reach.
    const privateSpaceId = await page.evaluate(async () => {
      const spaces = await fetch("/api/spaces").then((r) => r.json());
      return spaces[0].id;
    });

    // Another account, in a browser of its own.
    const other = await browser.newContext();
    const otherPage = await other.newPage();

    const otherEmail = uniqueEmail("other-owner");
    await otherPage.goto("/register");
    await hydrated(otherPage);
    await otherPage.getByLabel("Name").fill("Other Owner");
    await otherPage.getByLabel("Email").fill(otherEmail);
    await otherPage.getByLabel("Password").fill("a-good-password");
    await otherPage.getByRole("button", { name: /create account/i }).click();
    await expect(
      otherPage.getByRole("heading", { name: /check your email/i }),
    ).toBeVisible();
    await verifyEmailDirectly(otherEmail);
    await otherPage.goto("/login");
    await hydrated(otherPage);
    await otherPage.getByLabel("Email").fill(otherEmail);
    await otherPage.getByLabel("Password").fill("a-good-password");
    await otherPage.getByRole("button", { name: "Sign in" }).click();
    await expect(otherPage).toHaveURL(/\/dashboard$/);

    // The other owner sees their own (empty) inbox, not this Space's.
    await otherPage.goto("/inbox");
    await expect(otherPage.getByTestId("inbox-space")).toHaveCount(0);
    await expect(otherPage.getByText("No Spaces yet")).toBeVisible();

    // And the first owner's Space is not theirs: the API answers with a
    // 404 rather than handing over somebody else's testimonials
    // (PRD 4.7: only the Space owner can view or act on them).
    const status = await otherPage.evaluate(async (spaceId) => {
      const response = await fetch(`/api/spaces/${spaceId}/testimonials`);
      return response.status;
    }, privateSpaceId);
    expect(status).toBe(404);

    await other.close();
  });

  test("a read-only Space shows its testimonials but cannot be changed", async ({
    page,
  }) => {
    const token = runToken();
    const email = uniqueEmail("read-only");
    await registerAndVerify(page, email);

    // Three Spaces through the wizard, which is the Free plan's limit.
    for (let i = 1; i <= 3; i++) {
      await createSpace(page, {
        title: `Owned Space ${i}`,
        slug: `owned-space-${i}-${token}`,
      });
    }

    // A fourth, written straight to the database: the state a user is left
    // in when they downgrade and still own more Spaces than the plan
    // allows. The Free plan keeps the oldest three editable.
    const readOnlySpaceId = await createSpaceDirectly(
      email,
      `Locked Space ${token}`,
    );

    await openInbox(page);

    // The locked Space is in the picker.
    const picker = page.getByTestId("inbox-space");
    await expect(picker.locator("option")).toHaveCount(4);

    // Choose it.
    await picker.selectOption(readOnlySpaceId);
    await expect(page).toHaveURL(new RegExp(`space=${readOnlySpaceId}`));

    // A testimonial in it is visible.
    await insertTestimonial(
      readOnlySpaceId,
      "Locked One",
      "locked@example.com",
    );

    await page.reload();
    await expect(page.getByText("Locked One")).toBeVisible();

    // But it cannot be changed: the action buttons are gone, replaced by a
    // note saying why (PRD 4.7).
    await expect(
      page.getByText(/read-only, so its testimonials cannot be changed/i),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Favourite" })).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Delete" }),
    ).toHaveCount(0);
  });

  test("a signed-out visitor is sent to the login page", async ({ page }) => {
    await page.goto("/inbox");
    await expect(page).toHaveURL(/\/login\?target=/);
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

/**
 * Writes a Space straight to the database, past the plan's limit.
 *
 * <p>This is the state a user is left in when they downgrade: they still own
 * more Spaces than the plan allows. The wizard cannot create one, because it
 * refuses to go past the limit.
 *
 * @returns the new Space's id.
 */
async function createSpaceDirectly(
  ownerEmail: string,
  title: string,
): Promise<string> {
  const { execFileSync } = await import("node:child_process");

  const id = crypto.randomUUID();

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
      `insert into spaces (id, user_id, title, slug, theme_key, form_config,
              embed_config, public_profile_fields, rating_enabled, created_at, updated_at)
       select '${id}', id, '${title}', '${title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}',
              'light', '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, false, now(), now()
       from app_users where email = '${ownerEmail}'`,
    ],
    { stdio: "pipe" },
  );

  return id;
}

/**
 * Writes a testimonial straight to the database.
 *
 * <p>The owner cannot send one themselves -- submissions come from
 * visitors -- and a read-only Space cannot be submitted to, so this is the
 * only way to give a locked Space something to show.
 */
async function insertTestimonial(
  spaceId: string,
  name: string,
  email: string,
) {
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
      `insert into testimonials (id, space_id, name, email, message, rating,
              social_links, consent_given, favorite, wall_of_love, hidden,
              created_at, updated_at)
       values ('${crypto.randomUUID()}', '${spaceId}', '${name}', '${email}',
              'A testimonial in a read-only Space.', null, '[]'::jsonb, false,
              false, false, false, now(), now())`,
    ],
    { stdio: "pipe" },
  );
}
