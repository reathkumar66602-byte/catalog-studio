package com.catalogstudio.tips.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.tips.entity.TipVideo;
import com.catalogstudio.tips.entity.TipVideoSettings;
import com.catalogstudio.tips.repository.TipVideoRepository;
import com.catalogstudio.tips.repository.TipVideoSettingsRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TipVideoServiceTest {

    @Mock TipVideoRepository tipVideoRepository;
    @Mock TipVideoSettingsRepository settingsRepository;
    @Mock StorageService storageService;
    @Mock UserRepository userRepository;

    TipVideoService service;
    User admin;

    @BeforeEach
    void setUp() {
        service = new TipVideoService(tipVideoRepository, settingsRepository, storageService, userRepository);
        admin = User.builder()
                .id(9L)
                .uuid(UUID.randomUUID())
                .email("admin@test.com")
                .passwordHash("x")
                .role(User.Role.ADMIN)
                .status(User.UserStatus.ACTIVE)
                .build();
        AuthUser auth = AuthUser.from(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, auth.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadRejectsFileLargerThanTotalCap() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findBySettingsKey("default")).thenReturn(Optional.of(
                TipVideoSettings.builder().maxCount(5).maxTotalBytes(1_000_000).build()));
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.mp4", "video/mp4", new byte[1_000_001]);

        ApiException ex = assertThrows(ApiException.class, () -> service.upload("Too big", file));
        assertEquals(400, ex.getStatus().value());
        verify(storageService, never()).storeVideo(any());
    }

    @Test
    void uploadDeletesOldestWhenCountCapExceeded() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findBySettingsKey("default")).thenReturn(Optional.of(
                TipVideoSettings.builder().maxCount(1).maxTotalBytes(50_000_000).build()));
        when(tipVideoRepository.count()).thenReturn(1L, 0L, 0L);
        when(tipVideoRepository.sumSizeBytes()).thenReturn(0L);
        TipVideo oldest = TipVideo.builder()
                .uuid(UUID.randomUUID())
                .title("old")
                .storageKey(UUID.randomUUID() + ".mp4")
                .publicUrl("http://x/a.mp4")
                .contentType("video/mp4")
                .sizeBytes(100)
                .build();
        when(tipVideoRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(oldest));
        when(storageService.storeVideo(any())).thenReturn(
                new StorageService.StoredFile("new.mp4", "http://x/new.mp4", "video/mp4", 200, new byte[0]));
        when(tipVideoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "new.mp4", "video/mp4", new byte[200]);
        service.upload("Fresh tip", file);

        verify(tipVideoRepository).delete(oldest);
        verify(storageService).delete(oldest.getStorageKey());
        ArgumentCaptor<TipVideo> captor = ArgumentCaptor.forClass(TipVideo.class);
        verify(tipVideoRepository).save(captor.capture());
        assertEquals("Fresh tip", captor.getValue().getTitle());
        verify(storageService, times(1)).storeVideo(any());
    }
}
