/**
 * The animations shown on the thank-you page.
 *
 * The files are bundled with the application, in `public/gifs/`, rather than
 * fetched from a third party: the page is shown to every visitor, and a
 * hot-linked asset would leak the visitor's address to somebody else's
 * server and break whenever that server was down.
 *
 * This is a catalogue in the same shape as `lib/themes.ts`.
 */
export const THANK_YOU_GIFS: string[] = [
  "/gifs/thanks.gif",
  "/gifs/cheers.gif",
  "/gifs/well-done.gif",
  "/gifs/great.gif",
];

/**
 * One of the animations, picked at random.
 *
 * Called on the server, so the choice survives a refresh: a visitor who
 * reloads the page sees the same animation rather than a new one.
 */
export function randomThankYouGif(): string {
  return THANK_YOU_GIFS[Math.floor(Math.random() * THANK_YOU_GIFS.length)];
}
