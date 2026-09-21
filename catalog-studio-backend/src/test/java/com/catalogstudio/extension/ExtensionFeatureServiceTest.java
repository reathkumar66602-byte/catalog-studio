package com.catalogstudio.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.catalogstudio.analysis.repository.ProductAnalysisRepository;
import com.catalogstudio.extension.dto.FillGapsRequest;
import com.catalogstudio.extension.dto.VerifyShopRequest;
import com.catalogstudio.extension.entity.ExtensionUserSettings;
import com.catalogstudio.extension.repository.ExtensionInventoryPhotoRepository;
import com.catalogstudio.extension.repository.ExtensionUserSettingsRepository;
import com.catalogstudio.extension.service.ExtensionFeatureService;
import com.catalogstudio.extension.service.ExtensionService;
import com.catalogstudio.product.repository.ListingTemplateRepository;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtensionFeatureServiceTest {

    @Mock ExtensionService extensionService;
    @Mock ExtensionUserSettingsRepository settingsRepository;
    @Mock ExtensionInventoryPhotoRepository photoRepository;
    @Mock ListingTemplateRepository templateRepository;
    @Mock ProductAnalysisRepository analysisRepository;
    @Mock SubscriptionAccessService subscriptionAccessService;
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @InjectMocks ExtensionFeatureService featureService;

    @BeforeEach
    void bindUser() {
        when(extensionService.requireUserId(any())).thenReturn(1L);
        when(settingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User user = User.builder().id(1L).name("Seller").email("s@example.com").build();
        when(userRepository.getReferenceById(1L)).thenReturn(user);
    }

    @Test
    void firstShopLocksAndMismatchIsReported() {
        Map<String, Object> first = featureService.verifyShop("cst_x", new VerifyShopRequest("Krishna Store", "u1"));
        assertThat(first.get("status")).isEqualTo("ok");

        ExtensionUserSettings saved = ExtensionUserSettings.builder()
                .userId(1L)
                .settingsJson(Map.of("lockedShopName", "Krishna Store", "lockedShopUid", "u1"))
                .build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(saved));

        Map<String, Object> bad = featureService.verifyShop("cst_x", new VerifyShopRequest("Other Shop", "u2"));
        assertThat(bad.get("status")).isEqualTo("bad");
        assertThat(bad.get("code")).isEqualTo("lock_mismatch");
    }

    @Test
    void loginPanelChromeIsNotLockedAsAShop() {
        Map<String, Object> first = featureService.verifyShop(
                "cst_x", new VerifyShopRequest("Login to Meesho Supplier Panel", ""));
        assertThat(first.get("status")).isEqualTo("wait");

        Map<String, Object> real = featureService.verifyShop("cst_x", new VerifyShopRequest("Krishna Store", "u1"));
        assertThat(real.get("status")).isEqualTo("ok");
        assertThat(real.get("registered")).isEqualTo("Krishna Store");
    }

    @Test
    void poisonedLoginLockIsReplacedByRealShop() {
        ExtensionUserSettings saved = ExtensionUserSettings.builder()
                .userId(1L)
                .settingsJson(Map.of(
                        "lockedShopName", "Login to Meesho Supplier Panel",
                        "lockedShopUid", ""))
                .build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(saved));

        Map<String, Object> healed = featureService.verifyShop("cst_x", new VerifyShopRequest("Krishna Store", "u1"));
        assertThat(healed.get("status")).isEqualTo("ok");
        assertThat(healed.get("registered")).isEqualTo("Krishna Store");
        assertThat(healed.get("code")).isNull();
    }

    @Test
    void loginChromeDoesNotMismatchARealLock() {
        ExtensionUserSettings saved = ExtensionUserSettings.builder()
                .userId(1L)
                .settingsJson(Map.of("lockedShopName", "Krishna Store", "lockedShopUid", "u1"))
                .build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(saved));

        Map<String, Object> wait = featureService.verifyShop(
                "cst_x", new VerifyShopRequest("Login to Meesho Supplier Panel", ""));
        assertThat(wait.get("status")).isEqualTo("wait");
        assertThat(wait.get("registered")).isEqualTo("Krishna Store");
    }

    @Test
    void fillGapsAddsMissingFabric() {
        Map<String, Object> result = featureService.fillGaps("cst_x", new FillGapsRequest(
                "Women Kurti",
                "cotton",
                Map.of("title", "Kurti"),
                List.of("material", "gst")
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) result.get("fields");
        assertThat(fields.get("material")).isEqualTo("Cotton");
        assertThat(fields.get("gst")).isEqualTo("5");
    }
}
