import { describe, expect, it } from "vitest";
import { hasFeature, isStaff, isSuperAdmin, roleLabel } from "./roles";

describe("workspace roles", () => {
  it("treats admin and super admin as staff", () => {
    expect(isStaff("ADMIN")).toBe(true);
    expect(isStaff("SUPERADMIN")).toBe(true);
    expect(isStaff("SELLER")).toBe(false);
    expect(isSuperAdmin("SUPERADMIN")).toBe(true);
    expect(isSuperAdmin("ADMIN")).toBe(false);
  });

  it("hides a feature when it is not in the enabled list", () => {
    const user = { role: "SELLER", enabledFeatures: ["dashboard", "labels"] };
    expect(hasFeature(user, "meesho_calculator")).toBe(false);
    expect(hasFeature(user, "labels")).toBe(true);
    expect(hasFeature({ role: "ADMIN" }, "meesho_calculator")).toBe(true);
  });

  it("labels seller accounts as User", () => {
    expect(roleLabel("SELLER", "en")).toBe("User");
    expect(roleLabel("USER", "en")).toBe("User");
    expect(roleLabel("ADMIN", "en")).toBe("Admin");
    expect(roleLabel("SUPERADMIN", "en")).toBe("Super admin");
    expect(roleLabel("SUPERADMIN", "hi")).toBe("सुपर एडमिन");
  });
});
