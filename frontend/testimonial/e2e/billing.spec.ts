import { createHmac } from "node:crypto";

import { expect, test } from "@playwright/test";

/**
 * The Billing page (PRD 4.9).
 *
 * These tests drive the real page against the real backend. The account's
 * subscription is changed the way Stripe changes it -- with a webhook that
 * carries a real HMAC signature -- so the acceptance criterion in PRD 4.9 is
 * proved end to end: the redirect back from the checkout does not upgrade the
 * account, and only a verified webhook does.
 */

/**
 * The signing secret the backend verifies webhooks with.
 *
 * The local backend is started with this secret (see the README's "running the
 * tests"). It is not a Stripe secret and does not need to be: the webhook
 * endpoint verifies the signature itself and, for the event types used here,
 * reads everything it needs from the payload.
 */
const WEBHOOK_SECRET =
  process.env.STRIPE_WEBHOOK_SECRET ?? "whsec_e2e_test_secret";

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
 * The signed-in user's id.
 *
 * The webhook's metadata carries it, which is how Stripe events find their
 * way back to the account that started the checkout.
 */
async function userIdOf(page: import("@playwright/test").Page): Promise<string> {
  return page.evaluate(async () => {
    const response = await fetch("/api/auth/me");
    const user = await response.json();
    return user.id as string;
  });
}

/**
 * Signs a payload the way Stripe signs its webhooks.
 *
 * The signature is an HMAC-SHA256 over "<timestamp>.<payload>", keyed by the
 * endpoint's signing secret, and sent as `t=<timestamp>,v1=<hex>`.
 */
function sign(payload: string, secret: string): string {
  const timestamp = Math.floor(Date.now() / 1000);
  const mac = createHmac("sha256", secret);
  mac.update(`${timestamp}.${payload}`);
  return `t=${timestamp},v1=${mac.digest("hex")}`;
}

/**
 * A `customer.subscription.updated` event, as Stripe would send it.
 *
 * <p>This event type is used rather than `checkout.session.completed` because
 * everything the application needs is in the payload -- no call to Stripe is
 * made to handle it, so the test needs no Stripe account.
 */
function subscriptionUpdatedEvent(options: {
  eventId: string;
  userId: string;
  subscriptionId: string;
  customerId: string;
  status: string;
  currentPeriodEnd: number;
  cancelAtPeriodEnd?: boolean;
}): string {
  return JSON.stringify({
    id: options.eventId,
    object: "event",
    type: "customer.subscription.updated",
    created: Math.floor(Date.now() / 1000),
    data: {
      object: {
        id: options.subscriptionId,
        object: "subscription",
        customer: options.customerId,
        status: options.status,
        cancel_at_period_end: options.cancelAtPeriodEnd ?? false,
        current_period_end: options.currentPeriodEnd,
        metadata: { userId: options.userId },
        items: {
          object: "list",
          data: [
            {
              id: "si_test",
              object: "subscription_item",
              current_period_end: options.currentPeriodEnd,
            },
          ],
        },
      },
    },
  });
}

/**
 * Sends a webhook to the backend, signed with the real secret.
 *
 * @returns the response's status code.
 */
async function sendWebhook(
  request: import("@playwright/test").APIRequestContext,
  payload: string,
  options: { secret?: string; signature?: string } = {},
): Promise<number> {
  const signature =
    options.signature ?? sign(payload, options.secret ?? WEBHOOK_SECRET);

  const response = await request.post("/api/billing/webhooks/stripe", {
    headers: {
      "Content-Type": "application/json",
      "Stripe-Signature": signature,
    },
    data: payload,
  });

  return response.status();
}

/**
 * Opens the Billing page and waits for it to arrive.
 */
async function openBilling(page: import("@playwright/test").Page) {
  await page.goto("/billing");
  await hydrated(page);
  await expect(page.getByRole("heading", { name: "Billing" })).toBeVisible();
}

test.describe("the billing page", () => {
  test("a Free user sees the Free plan, its limits and both plans", async ({
    page,
  }) => {
    await registerAndVerify(page, uniqueEmail("billing-free"));

    await openBilling(page);

    // The current plan is Free, and it is marked as such (PRD 4.9).
    await expect(page.getByTestId("billing-plan-free")).toBeVisible();
    await expect(page.getByTestId("billing-premium-badge")).toHaveCount(0);

    // The Free plan's limits are spelled out.
    await expect(
      page.getByText(/3 Spaces and collect up to 100 testimonials/i),
    ).toBeVisible();

    // Both plans are on offer, with their features and prices.
    await expect(page.getByTestId("billing-plan-free")).toContainText("$0");
    await expect(page.getByTestId("billing-plan-premium")).toContainText(
      "$9.99/month",
    );
    await expect(
      page.getByTestId("billing-plan-premium").getByText("25 Spaces"),
    ).toBeVisible();
    await expect(
      page.getByTestId("billing-plan-premium").getByText(
        "Masonry and Carousel embed layouts",
      ),
    ).toBeVisible();

    // And there is a way to upgrade.
    await expect(page.getByTestId("billing-upgrade")).toBeVisible();
  });

  test("the success redirect alone never upgrades the account", async ({
    page,
  }) => {
    await registerAndVerify(page, uniqueEmail("billing-redirect"));

    // The Billing page has been read, so the account has a subscription row.
    await openBilling(page);

    // This is the redirect Stripe sends the browser back to after a
    // successful checkout. It carries no proof of anything (PRD 4.9).
    await page.goto("/billing?checkout=success");
    await hydrated(page);

    // The account is still Free, because only a verified webhook may change
    // the plan (PRD 4.9, 6.15).
    await expect(page.getByTestId("billing-plan-free")).toBeVisible();
    await expect(page.getByTestId("billing-premium-badge")).toHaveCount(0);

    // And the database agrees -- the claim is not just what the page shows.
    const plan = await page.evaluate(async () => {
      const response = await fetch("/api/billing");
      const state = await response.json();
      return state.plan as string;
    });
    expect(plan).toBe("FREE");
  });

  test("a verified webhook upgrades the account", async ({ page, request }) => {
    await registerAndVerify(page, uniqueEmail("billing-upgrade"));

    await openBilling(page);

    const userId = await userIdOf(page);
    const eventId = `evt_e2e_${Date.now()}`;

    const status = await sendWebhook(
      request,
      subscriptionUpdatedEvent({
        eventId,
        userId,
        subscriptionId: `sub_e2e_${Date.now()}`,
        customerId: `cus_e2e_${Date.now()}`,
        status: "active",
        currentPeriodEnd: Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60,
      }),
    );

    expect(status).toBe(200);

    // The page now shows Premium, with its limits.
    await page.reload();
    await hydrated(page);

    await expect(page.getByTestId("billing-premium-badge")).toBeVisible();
    await expect(
      page.getByText(/25 Spaces and collect up to 1,000 testimonials/i),
    ).toBeVisible();

    // The upgrade choice is gone: there is nothing left to upgrade to.
    await expect(page.getByTestId("billing-upgrade")).toHaveCount(0);
  });

  test("a webhook with a bad signature is refused and changes nothing", async ({
    page,
    request,
  }) => {
    await registerAndVerify(page, uniqueEmail("billing-badsig"));

    await openBilling(page);

    const userId = await userIdOf(page);

    const status = await sendWebhook(
      request,
      subscriptionUpdatedEvent({
        eventId: `evt_e2e_bad_${Date.now()}`,
        userId,
        subscriptionId: `sub_e2e_bad_${Date.now()}`,
        customerId: `cus_e2e_bad_${Date.now()}`,
        status: "active",
        currentPeriodEnd: Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60,
      }),
      { secret: "whsec_not_the_real_secret" },
    );

    expect(status).toBe(400);

    // The account is untouched: still Free, and no Premium badge.
    await page.reload();
    await hydrated(page);
    await expect(page.getByTestId("billing-premium-badge")).toHaveCount(0);
    await expect(page.getByTestId("billing-plan-free")).toBeVisible();
  });

  test("a replayed webhook does not change the account a second time", async ({
    page,
    request,
  }) => {
    await registerAndVerify(page, uniqueEmail("billing-replay"));

    await openBilling(page);

    const userId = await userIdOf(page);
    const eventId = `evt_e2e_replay_${Date.now()}`;
    const subscriptionId = `sub_e2e_replay_${Date.now()}`;
    const customerId = `cus_e2e_replay_${Date.now()}`;
    const periodEnd = Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60;

    const payload = subscriptionUpdatedEvent({
      eventId,
      userId,
      subscriptionId,
      customerId,
      status: "active",
      currentPeriodEnd: periodEnd,
    });

    expect(await sendWebhook(request, payload)).toBe(200);
    expect(await sendWebhook(request, payload)).toBe(200);

    // The second delivery was accepted, but it did not apply the change
    // twice: the account is Premium, and the subscription id was recorded
    // once (PRD 6.14).
    await page.reload();
    await hydrated(page);
    await expect(page.getByTestId("billing-premium-badge")).toBeVisible();

    const rows = await page.evaluate(async () => {
      const response = await fetch("/api/billing");
      const state = await response.json();
      return state as { plan: string; stripeCustomerId: string | null };
    });
    expect(rows.plan).toBe("PREMIUM");
    expect(rows.stripeCustomerId).toBe(customerId);
  });

  test("a past-due payment keeps Premium and shows a banner", async ({
    page,
    request,
  }) => {
    await registerAndVerify(page, uniqueEmail("billing-pastdue"));

    await openBilling(page);

    const userId = await userIdOf(page);

    expect(
      await sendWebhook(
        request,
        subscriptionUpdatedEvent({
          eventId: `evt_e2e_pd_${Date.now()}`,
          userId,
          subscriptionId: `sub_e2e_pd_${Date.now()}`,
          customerId: `cus_e2e_pd_${Date.now()}`,
          status: "active",
          currentPeriodEnd: Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60,
        }),
      ),
    ).toBe(200);

    // The payment fails. Stripe is still retrying, so the account stays
    // Premium (PRD 4.9).
    expect(
      await sendWebhook(
        request,
        subscriptionUpdatedEvent({
          eventId: `evt_e2e_pastdue_${Date.now()}`,
          userId,
          subscriptionId: `sub_e2e_pd_${Date.now()}`,
          customerId: `cus_e2e_pd_${Date.now()}`,
          status: "past_due",
          currentPeriodEnd: Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60,
        }),
      ),
    ).toBe(200);

    await page.reload();
    await hydrated(page);

    await expect(page.getByTestId("billing-past-due")).toBeVisible();
    await expect(page.getByTestId("billing-premium-badge")).toBeVisible();
  });

  test("a cancellation shows the end date and can be undone", async ({
    page,
    request,
  }) => {
    await registerAndVerify(page, uniqueEmail("billing-cancel"));

    await openBilling(page);

    const userId = await userIdOf(page);
    const subscriptionId = `sub_e2e_cancel_${Date.now()}`;
    const customerId = `cus_e2e_cancel_${Date.now()}`;

    expect(
      await sendWebhook(
        request,
        subscriptionUpdatedEvent({
          eventId: `evt_e2e_cancel_${Date.now()}`,
          userId,
          subscriptionId,
          customerId,
          status: "active",
          currentPeriodEnd: Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60,
        }),
      ),
    ).toBe(200);

    // Stripe says the subscription is set to end at the close of the period.
    const periodEnd = Math.floor(Date.now() / 1000) + 30 * 24 * 60 * 60;
    expect(
      await sendWebhook(
        request,
        subscriptionUpdatedEvent({
          eventId: `evt_e2e_cancelflag_${Date.now()}`,
          userId,
          subscriptionId,
          customerId,
          status: "active",
          currentPeriodEnd: periodEnd,
          cancelAtPeriodEnd: true,
        }),
      ),
    ).toBe(200);

    await page.reload();
    await hydrated(page);

    // The page says the subscription is set to end, and when (PRD 4.9).
    const banner = page.getByTestId("billing-cancel-scheduled");
    await expect(banner).toBeVisible();
    await expect(banner).toContainText(/premium access continues until then/i);

    // Premium continues until the period ends.
    await expect(page.getByTestId("billing-premium-badge")).toBeVisible();

    // The end date is shown, so the owner knows how long they have.
    const expectedDate = new Date(periodEnd * 1000).toLocaleDateString("en-GB", {
      day: "numeric",
      month: "long",
      year: "numeric",
    });
    await expect(banner).toContainText(expectedDate);

    // And the cancellation can be undone before the date passes.
    await expect(page.getByTestId("billing-resume")).toBeVisible();
  });

  test("a signed-out visitor is sent to the login page", async ({ page }) => {
    await page.goto("/billing");
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
