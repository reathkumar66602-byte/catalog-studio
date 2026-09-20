import { describe, expect, it } from "vitest";
import { passwordLabel, passwordScore } from "./password";

describe("password strength", () => {
  it("scores weak short passwords", () => {
    expect(passwordScore("abc")).toBe(0);
    expect(passwordLabel(0)).toBe("Too weak");
  });

  it("scores strong mixed passwords", () => {
    expect(passwordScore("Admin@123")).toBe(4);
    expect(passwordLabel(4)).toBe("Excellent");
  });
});
