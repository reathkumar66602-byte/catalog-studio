import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(__dirname, "../../../../catalog-studio-backend/src");
const optionsSrc = readFileSync(resolve(root, "main/java/com/catalogstudio/shoot/service/ShootOptions.java"), "utf8");
const promptSrc = readFileSync(resolve(root, "main/java/com/catalogstudio/shoot/service/ShootPrompt.java"), "utf8");
const planSrc = readFileSync(resolve(root, "main/java/com/catalogstudio/shoot/service/ShootPlan.java"), "utf8");
const testSrc = readFileSync(resolve(root, "test/java/com/catalogstudio/shoot/ShootPlanTest.java"), "utf8");
const controllerSrc = readFileSync(resolve(root, "main/java/com/catalogstudio/shoot/controller/ShootController.java"), "utf8");

describe("meesho shoot backend contract", () => {
  it("defaults meesho background to AUTO and keeps fixed scenes", () => {
    expect(optionsSrc).toMatch(/DEFAULT_MEESHO_BACKGROUND\s*=\s*"AUTO"/);
    expect(optionsSrc).toContain('"AUTO"');
    expect(optionsSrc).toContain('"FESTIVE_HOME"');
    expect(optionsSrc).toContain('"LIVING_ROOM"');
    expect(optionsSrc).toContain('"COURTYARD"');
  });

  it("auto prompt asks the model to match background to the garment", () => {
    expect(promptSrc).toContain('"AUTO".equals(choice)');
    expect(promptSrc).toContain("flatters this exact outfit");
    expect(promptSrc).toContain("Study the garment colour, print, fabric, occasion, and style");
    expect(promptSrc).toContain("cute Indian");
  });

  it("fixed scenes still use an exact setting string", () => {
    expect(promptSrc).toContain("Use this exact setting");
    expect(promptSrc).toContain("marigold");
    expect(promptSrc).toContain("living room");
    expect(promptSrc).toContain("courtyard");
  });

  it("flipkart white background path remains intact", () => {
    expect(promptSrc).toContain("pure white, RGB 255 255 255");
    expect(promptSrc).toContain("Flipkart and Amazon");
    expect(planSrc).toContain("flipkart ? ShootOptions.DEFAULT_MEESHO_BACKGROUND");
  });

  it("api accepts meeshoBackground and unit tests cover auto plus fixed scenes", () => {
    expect(controllerSrc).toContain('value = "meeshoBackground"');
    expect(testSrc).toContain("defaultShootUsesAutoMeeshoBackgroundAndCuteModel");
    expect(testSrc).toContain("blankMeeshoBackgroundDefaultsToAuto");
    expect(testSrc).toContain("meeshoBackgroundChoiceIsWrittenIntoThePrompt");
    expect(testSrc).toContain("flipkartIgnoresMeeshoBackgroundChoice");
  });
});
