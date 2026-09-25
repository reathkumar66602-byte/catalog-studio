import { describe, expect, it } from "vitest";
import { DEFAULT_MEESHO_BACKGROUND, MEESHO_BACKGROUNDS, plannedImageCount } from "./ShootPage";

describe("shoot image count", () => {
  it("counts the selected angles when Flipkart white background is on", () => {
    expect(
      plannedImageCount({
        angles: ["FRONT", "SIDE", "SHOP"],
        flipkart: true,
        mode: "SINGLE",
        productCount: 1,
        trial: false,
      }),
    ).toBe(3);
  });

  it("counts the same angles for the default Meesho background", () => {
    expect(
      plannedImageCount({
        angles: ["FRONT"],
        flipkart: false,
        mode: "SINGLE",
        productCount: 1,
        trial: false,
      }),
    ).toBe(1);
  });

  it("limits a free trial to one image", () => {
    expect(
      plannedImageCount({
        angles: ["FRONT", "SIDE", "SHOP"],
        flipkart: true,
        mode: "SINGLE",
        productCount: 1,
        trial: true,
      }),
    ).toBe(1);
  });

  it("does not add an extra image per combo product", () => {
    expect(
      plannedImageCount({
        angles: ["FRONT"],
        flipkart: true,
        mode: "COMBO",
        productCount: 3,
        trial: false,
      }),
    ).toBe(1);
  });
});

describe("meesho background options", () => {
  it("defaults to auto style-matched background", () => {
    expect(DEFAULT_MEESHO_BACKGROUND).toBe("AUTO");
    expect(MEESHO_BACKGROUNDS[0].id).toBe("AUTO");
  });

  it("keeps the fixed Meesho scenes after auto", () => {
    expect(MEESHO_BACKGROUNDS.map((bg) => bg.id)).toEqual([
      "AUTO",
      "FESTIVE_HOME",
      "LIVING_ROOM",
      "COURTYARD",
    ]);
  });
});
