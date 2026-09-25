package com.catalogstudio.shoot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catalogstudio.shoot.service.ShootOptions;
import com.catalogstudio.shoot.service.ShootPlan;
import com.catalogstudio.shoot.service.ShootPrompt;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShootPlanTest {

    private static final ShootPlan.Ref FRONT = new ShootPlan.Ref("garment-1.jpg", "image/jpeg", new byte[] {1});
    private static final ShootPlan.Ref SECOND = new ShootPlan.Ref("garment-2.jpg", "image/jpeg", new byte[] {2});
    private static final ShootPlan.Ref BACK = new ShootPlan.Ref("back.jpg", "image/jpeg", new byte[] {3});

    @Test
    void singleShootKeepsGarmentAndAddsMarketplaceAfterAngles() {
        List<ShootPlan.Job> jobs = ShootPlan.jobs(
                "SINGLE",
                "26-32",
                List.of("FRONT", "SHOP"),
                true,
                false,
                List.of(FRONT),
                null);

        assertThat(jobs).extracting(ShootPlan.Job::kind).containsExactly("FRONT", "SHOP");
        assertThat(jobs.get(0).prompt()).contains("pure white");
        assertThat(jobs.get(0).prompt()).contains("Flipkart and Amazon");
        assertThat(jobs.get(1).prompt()).contains("pure white");
        assertThat(jobs.get(0).prompt()).contains("26-32 years");
        assertThat(jobs.get(0).prompt()).contains("Do not crop");
        assertThat(jobs.get(0).prompt()).contains("saree");
        assertThat(jobs.get(0).size()).isEqualTo(ShootPlan.AUTO);
        assertThat(jobs.get(0).prompt()).doesNotContain("extra instruction");
    }

    @Test
    void defaultShootUsesAutoMeeshoBackgroundAndCuteModel() {
        List<ShootPlan.Job> jobs = ShootPlan.jobs(
                "SINGLE",
                "26-32",
                List.of("FRONT"),
                false,
                false,
                List.of(FRONT),
                null);

        assertThat(jobs).extracting(ShootPlan.Job::kind).containsExactly("FRONT");
        assertThat(jobs.get(0).prompt()).contains("Meesho");
        assertThat(jobs.get(0).prompt()).contains("not a plain studio");
        assertThat(jobs.get(0).prompt()).contains("flatters this exact outfit");
        assertThat(jobs.get(0).prompt()).contains("cute Indian adult");
        assertThat(jobs.get(0).prompt()).contains("Indian");
        assertThat(jobs.get(0).prompt()).contains("must not be plain white or pure white");
        assertThat(jobs.get(0).prompt()).doesNotContain("RGB 255 255 255");
        assertThat(jobs.get(0).prompt()).doesNotContain("Flipkart and Amazon");
        assertThat(jobs.get(0).prompt()).doesNotContain("Use this exact setting");
    }

    @Test
    void blankMeeshoBackgroundDefaultsToAuto() {
        assertThat(ShootOptions.meeshoBackground(null)).isEqualTo("AUTO");
        assertThat(ShootOptions.meeshoBackground("")).isEqualTo("AUTO");
        assertThat(ShootOptions.DEFAULT_MEESHO_BACKGROUND).isEqualTo("AUTO");
        assertThat(ShootOptions.MEESHO_BACKGROUNDS).containsExactly(
                "AUTO", "FESTIVE_HOME", "LIVING_ROOM", "COURTYARD");
    }

    @Test
    void meeshoBackgroundChoiceIsWrittenIntoThePrompt() {
        List<ShootPlan.Job> living = ShootPlan.jobs(
                "SINGLE",
                "5-6",
                List.of("FRONT"),
                false,
                false,
                List.of(FRONT),
                null,
                "LIVING_ROOM");

        assertThat(living.get(0).prompt()).contains("living room");
        assertThat(living.get(0).prompt()).contains("Use this exact setting");
        assertThat(living.get(0).prompt()).contains("cute Indian child");
        assertThat(living.get(0).prompt()).doesNotContain("flatters this exact outfit");

        List<ShootPlan.Job> festive = ShootPlan.jobs(
                "SINGLE",
                "5-6",
                List.of("FRONT"),
                false,
                false,
                List.of(FRONT),
                null,
                "FESTIVE_HOME");

        assertThat(festive.get(0).prompt()).contains("marigold");
        assertThat(festive.get(0).prompt()).contains("Use this exact setting");

        List<ShootPlan.Job> courtyard = ShootPlan.jobs(
                "SINGLE",
                "5-6",
                List.of("FRONT"),
                false,
                false,
                List.of(FRONT),
                null,
                "COURTYARD");

        assertThat(courtyard.get(0).prompt()).contains("courtyard");
        assertThat(courtyard.get(0).prompt()).contains("Use this exact setting");
    }

    @Test
    void flipkartIgnoresMeeshoBackgroundChoice() {
        List<ShootPlan.Job> jobs = ShootPlan.jobs(
                "SINGLE",
                "26-32",
                List.of("FRONT"),
                true,
                false,
                List.of(FRONT),
                null,
                "FESTIVE_HOME");

        assertThat(jobs.get(0).prompt()).contains("pure white");
        assertThat(jobs.get(0).prompt()).contains("Flipkart and Amazon");
        assertThat(jobs.get(0).prompt()).doesNotContain("marigold");
        assertThat(jobs.get(0).prompt()).doesNotContain("flatters this exact outfit");
    }

    @Test
    void trialKeepsOnlyTheFirstSelectedShot() {
        List<ShootPlan.Job> jobs = ShootPlan.jobs(
                "SINGLE",
                "20-25",
                ShootOptions.angles(List.of("side", "front"), false),
                true,
                true,
                List.of(FRONT),
                BACK);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).kind()).isEqualTo("FRONT");
    }

    @Test
    void comboMarketplaceMakesOneWhiteBackgroundImagePerProduct() {
        List<ShootPlan.Job> jobs = ShootPlan.jobs(
                "COMBO",
                "33-40",
                List.of("FRONT"),
                true,
                false,
                List.of(FRONT, SECOND),
                null);

        assertThat(jobs).extracting(ShootPlan.Job::kind).containsExactly("FRONT");
        assertThat(jobs.get(0).prompt()).contains("pure white");
        assertThat(jobs.get(0).references()).containsExactly(FRONT, SECOND);
    }

    @Test
    void childCatalogPromptStaysAModestClothingShoot() {
        String prompt = ShootPrompt.forKind("FRONT", "5-6");
        assertThat(prompt).contains("cute Indian child");
        assertThat(prompt).contains("modest children's clothing catalog");
        assertThat(prompt).contains("everyday kidswear only");
        assertThat(prompt).contains("flatters this exact outfit");
    }

    @Test
    void unknownMeeshoBackgroundIsRejected() {
        assertThatThrownBy(() -> ShootOptions.meeshoBackground("beach"))
                .hasMessageContaining("Meesho background");
    }

    @Test
    void unknownAgeIsRejected() {
        assertThatThrownBy(() -> ShootOptions.modelAge("teen"))
                .hasMessageContaining("model age");
    }
}
