"use client";

import { useRef, useState } from "react";

/**
 * The public URL field and its copy button.
 *
 * The clipboard API is not available on every browser and needs a secure
 * context, so the button falls back to selecting the text and saying so
 * rather than failing silently (PRD 4.2: "copy-link copies it to the
 * clipboard").
 */
export function CopyLinkButton({ url }: { url: string }) {
  const fieldRef = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");

  async function onCopy() {
    try {
      await navigator.clipboard.writeText(url);
      setState("copied");
    } catch {
      // The browser refused the clipboard. Selecting the text lets the user
      // copy it by hand instead.
      fieldRef.current?.select();
      setState("failed");
    }

    setTimeout(() => setState("idle"), 2500);
  }

  return (
    <div>
      <input
        id="public-url"
        ref={fieldRef}
        readOnly
        value={url}
        onFocus={(event) => event.currentTarget.select()}
        className="mt-2 block w-full rounded-lg border border-zinc-300 bg-zinc-50 px-3 py-2 font-mono text-sm text-zinc-900 outline-none dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
      />

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={onCopy}
          className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-zinc-700 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {state === "copied"
            ? "Copied!"
            : state === "failed"
              ? "Press Ctrl+C to copy"
              : "Copy link"}
        </button>

        {state === "failed" ? (
          <span className="text-sm text-zinc-500 dark:text-zinc-400">
            Your browser blocked the clipboard. The link is selected.
          </span>
        ) : null}
      </div>
    </div>
  );
}
