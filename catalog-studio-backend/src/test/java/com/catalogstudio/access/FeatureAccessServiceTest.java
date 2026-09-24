package com.catalogstudio.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.catalogstudio.access.entity.UserFeatureAccess;
import com.catalogstudio.access.repository.UserFeatureAccessRepository;
import com.catalogstudio.access.service.FeatureAccessService;
import com.catalogstudio.user.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureAccessServiceTest {

    @Mock UserFeatureAccessRepository accessRepository;
    @InjectMocks FeatureAccessService featureAccessService;

    @Test
    void missingRowsKeepEveryFeatureEnabled() {
        User seller = User.builder().id(11L).role(User.Role.SELLER).build();
        when(accessRepository.findByUser_Id(11L)).thenReturn(List.of());

        Map<String, Boolean> map = featureAccessService.mapFor(seller);

        assertThat(map.get("meesho_calculator")).isTrue();
        assertThat(featureAccessService.enabledKeys(seller)).contains("meesho_calculator", "labels", "dashboard");
    }

    @Test
    void storedFalseHidesCalculator() {
        User seller = User.builder().id(11L).role(User.Role.SELLER).build();
        when(accessRepository.findByUser_Id(11L)).thenReturn(List.of(
                UserFeatureAccess.builder().user(seller).featureKey("meesho_calculator").enabled(false).build()
        ));

        assertThat(featureAccessService.mapFor(seller).get("meesho_calculator")).isFalse();
        assertThat(featureAccessService.enabledKeys(seller)).doesNotContain("meesho_calculator");
    }

    @Test
    void staffAlwaysReceivesFullAccess() {
        User admin = User.builder().id(1L).role(User.Role.SUPERADMIN).build();
        Map<String, Boolean> map = featureAccessService.mapFor(admin);
        assertThat(map.values()).allMatch(Boolean.TRUE::equals);
    }

    @Test
    void replaceHidesCalculatorAndKeepsOtherDefaults() {
        User seller = User.builder().id(11L).role(User.Role.SELLER).build();
        when(accessRepository.findByUser_Id(11L)).thenReturn(List.of());
        when(accessRepository.findByUser_IdAndFeatureKey(11L, "meesho_calculator")).thenReturn(Optional.empty());
        when(accessRepository.save(any(UserFeatureAccess.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Boolean> saved = featureAccessService.replace(seller, Map.of("meesho_calculator", false));
        assertThat(saved.get("meesho_calculator")).isFalse();
        assertThat(saved.get("labels")).isTrue();
    }

    @Test
    void replaceRejectsUnknownFeatureKeys() {
        User seller = User.builder().id(11L).role(User.Role.SELLER).build();
        when(accessRepository.findByUser_Id(11L)).thenReturn(List.of());
        assertThatThrownBy(() -> featureAccessService.replace(seller, Map.of("secret_admin", false)))
                .hasMessageContaining("Unknown feature");
    }
}
