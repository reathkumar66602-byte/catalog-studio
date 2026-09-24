import { describe, expect, it } from "vitest";
import { plannedImageCount } from "./ShootPage";

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
