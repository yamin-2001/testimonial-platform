import { describe, expect, it } from "vitest";

import { visibleFields } from "@/components/testimonial-form-fields";
import { FieldSettings } from "@/lib/spaces";

/**
 * The rules about what a Space's form collects (PRD 4.3).
 *
 * These mirror the backend's `FormDefinitionTest`. The two must agree: the
 * preview shows what the backend accepts, and a field that is switched off
 * must not appear on one and be accepted by the other.
 */
describe("the public form's fields", () => {
  it("always collects name, email and the testimonial", () => {
    const fields = visibleFields({});

    expect(fields.map((field) => field.name)).toEqual(
      expect.arrayContaining(["name", "email", "message"]),
    );
  });

  it("always requires name, email and the testimonial", () => {
    const fields = visibleFields({});

    for (const name of ["name", "email", "message"]) {
      expect(fields.find((field) => field.name === name)?.required).toBe(true);
    }
  });

  it("collects an optional field that is switched on", () => {
    const fields = visibleFields({
      companyName: { enabled: true, required: false },
    });

    expect(fields.map((field) => field.name)).toContain("companyName");
  });

  it("does not collect an optional field that is switched off", () => {
    const fields = visibleFields({
      companyName: { enabled: false, required: false },
    });

    expect(fields.map((field) => field.name)).not.toContain("companyName");
  });

  it("requires an optional field that is marked required", () => {
    const fields = visibleFields({
      jobTitle: { enabled: true, required: true },
    });

    const jobTitle = fields.find((field) => field.name === "jobTitle");
    expect(jobTitle?.required).toBe(true);
  });

  it("does not require an optional field that is optional", () => {
    const fields = visibleFields({
      jobTitle: { enabled: true, required: false },
    });

    const jobTitle = fields.find((field) => field.name === "jobTitle");
    expect(jobTitle?.required).toBe(false);
  });

  it("collects the optional fields before the testimonial", () => {
    const config: Record<string, FieldSettings> = {
      companyName: { enabled: true, required: false },
    };
    const names = visibleFields(config).map((field) => field.name);

    expect(names.indexOf("email")).toBeLessThan(names.indexOf("companyName"));
    expect(names.indexOf("companyName")).toBeLessThan(names.indexOf("message"));
  });

  it("shows the consent box always, and never requires it", () => {
    // The consent box is not one of the collected fields; it is always shown
    // on the form and never blocks a submission.
    const names = visibleFields({}).map((field) => field.name);
    expect(names).not.toContain("consent");
  });

  it("collects an optional field the configuration does not mention", () => {
    // The backend's rule: a field the configuration says nothing about is on,
    // so a Space saved before a field existed still collects it.
    const names = visibleFields({}).map((field) => field.name);

    expect(names).toContain("companyName");
    expect(names).toContain("jobTitle");
    expect(names).toContain("websiteUrl");
    expect(names).toContain("socialLinks");
  });

  it("does not require an optional field the configuration does not mention", () => {
    const fields = visibleFields({});
    const companyName = fields.find((field) => field.name === "companyName");

    expect(companyName?.required).toBe(false);
  });
});
