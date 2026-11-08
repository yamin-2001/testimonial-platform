import { Suspense } from "react";

import { VerifyEmailForm } from "./verify-email-form";

/**
 * The email verification page.
 *
 * The token comes from the query string, so it is wrapped in a Suspense
 * boundary.
 */
export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailForm />
    </Suspense>
  );
}
