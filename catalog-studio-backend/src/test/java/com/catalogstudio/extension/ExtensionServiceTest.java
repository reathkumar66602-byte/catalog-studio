package com.catalogstudio.extension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.common.util.TokenHasher;
import com.catalogstudio.extension.entity.ExtensionDevice;
import com.catalogstudio.extension.repository.ExtensionActivityLogRepository;
import com.catalogstudio.extension.repository.ExtensionDeviceRepository;
import com.catalogstudio.extension.service.ExtensionService;
import com.catalogstudio.product.repository.AutofillProfileRepository;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtensionServiceTest {

    @Mock ExtensionDeviceRepository deviceRepository;
    @Mock ExtensionActivityLogRepository activityLogRepository;
    @Mock ProductRepository productRepository;
    @Mock AutofillProfileRepository profileRepository;
    @Mock UserRepository userRepository;
    @Mock com.catalogstudio.business.repository.BusinessRepository businessRepository;
    @InjectMocks ExtensionService extensionService;

    @Test
    void revokedKeyIsRejected() {
        ExtensionDevice device = ExtensionDevice.builder()
                .extensionKeyHash(TokenHasher.sha256("cst_revoked"))
                .status(ExtensionDevice.DeviceStatus.REVOKED)
                .deviceName("Chrome")
                .build();
        when(deviceRepository.findByExtensionKeyHash(any())).thenReturn(Optional.of(device));
        assertThatThrownBy(() -> extensionService.heartbeat("cst_revoked"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("revoked");
    }
}
