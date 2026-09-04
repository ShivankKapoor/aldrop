package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.DTO.Request.CreatePlatformRequestDTO;
import com.shivankkapoor.aldrop.Exception.PlatformNameTakenException;
import com.shivankkapoor.aldrop.Exception.PlatformNotFoundException;
import com.shivankkapoor.aldrop.Repository.PlatformRepository;
import com.shivankkapoor.aldrop.Security.TokenGenerator;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private TokenGenerator tokenGenerator;

    @InjectMocks
    private PlatformService platformService;

    @Test
    void createsPlatformWithGeneratedApiKeyAndDefaultTtl() {
        CreatePlatformRequestDTO request = new CreatePlatformRequestDTO();
        request.setName("acme");
        request.setMaxSessionsPerUser(5);
        request.setTotpAvailable(true);
        request.setRequireDeviceBinding(true);

        when(platformRepository.findByName("acme")).thenReturn(Optional.empty());
        when(platformRepository.save(any(Platform.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenGenerator.generate(anyInt())).thenReturn("mock-api-key");

        Platform saved = platformService.create(request);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("acme");
        assertThat(saved.getApiKey()).isEqualTo("mock-api-key");
        assertThat(saved.getSessionTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(saved.getMaxSessionsPerUser()).isEqualTo(5);
        assertThat(saved.isTotpAvailable()).isTrue();
        assertThat(saved.isRequireDeviceBinding()).isTrue();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void usesProvidedSessionTtlWhenSet() {
        CreatePlatformRequestDTO request = new CreatePlatformRequestDTO();
        request.setName("acme");
        request.setSessionTtl(Duration.ofMinutes(90));

        when(platformRepository.findByName("acme")).thenReturn(Optional.empty());
        when(platformRepository.save(any(Platform.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenGenerator.generate(anyInt())).thenReturn("mock-api-key");

        Platform saved = platformService.create(request);

        assertThat(saved.getSessionTtl()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void rejectsCreateWhenNameAlreadyTaken() {
        CreatePlatformRequestDTO request = new CreatePlatformRequestDTO();
        request.setName("acme");

        when(platformRepository.findByName("acme")).thenReturn(Optional.of(new Platform()));

        assertThatThrownBy(() -> platformService.create(request))
                .isInstanceOf(PlatformNameTakenException.class);

        verify(platformRepository, never()).save(any());
    }

    @Test
    void updatesActiveStatusWhenPlatformExists() {
        UUID id = UUID.randomUUID();
        Platform platform = new Platform();
        platform.setId(id);
        platform.setActive(true);

        when(platformRepository.findById(id)).thenReturn(Optional.of(platform));
        when(platformRepository.save(any(Platform.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Platform result = platformService.updateActiveStatus(id, false);

        assertThat(result.isActive()).isFalse();
        ArgumentCaptor<Platform> captor = ArgumentCaptor.forClass(Platform.class);
        verify(platformRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void throwsWhenUpdatingStatusOfUnknownPlatform() {
        UUID id = UUID.randomUUID();
        when(platformRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformService.updateActiveStatus(id, true))
                .isInstanceOf(PlatformNotFoundException.class);

        verify(platformRepository, never()).save(any());
    }

    @Test
    void deletesPlatformWhenItExists() {
        UUID id = UUID.randomUUID();
        Platform platform = new Platform();
        platform.setId(id);

        when(platformRepository.findById(id)).thenReturn(Optional.of(platform));

        platformService.delete(id);

        verify(platformRepository).delete(platform);
    }

    @Test
    void throwsWhenDeletingUnknownPlatform() {
        UUID id = UUID.randomUUID();
        when(platformRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformService.delete(id))
                .isInstanceOf(PlatformNotFoundException.class);

        verify(platformRepository, never()).delete(any());
    }
}
