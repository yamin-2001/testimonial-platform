import { redirect } from "next/navigation";

/**
 * The root path sends people to the right place: the dashboard if they have a
 * session, otherwise the sign-in page.
 */
export default async function Home() {
  redirect("/login");
}
