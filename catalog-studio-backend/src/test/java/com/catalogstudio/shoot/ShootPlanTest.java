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
    void defaultShootUsesAStyledMeeshoBackground() {
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
        assertThat(jobs.get(0).prompt()).contains("Indian");
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
        assertThat(prompt).contains("Indian child");
        assertThat(prompt).contains("modest children's clothing catalog");
        assertThat(prompt).contains("Everyday kidswear only");
    }

    @Test
    void unknownAgeIsRejected() {
        assertThatThrownBy(() -> ShootOptions.modelAge("teen"))
                .hasMessageContaining("model age");
    }
}
