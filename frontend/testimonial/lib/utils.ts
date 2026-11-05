import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merge conditional class names while letting later Tailwind utilities
 * override earlier ones (e.g. `cn("p-2", "p-4")` -> `"p-4"`).
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
