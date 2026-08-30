package com.shivankkapoor.aldrop.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.RegisterUserRequestDTO;
import com.shivankkapoor.aldrop.Exception.UsernameTakenException;
import com.shivankkapoor.aldrop.Repository.UserRepository;
import com.shivankkapoor.aldrop.Security.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private UserService userService;

    private final UUID platformId = UUID.randomUUID();

    @Test
    void registersUserWithLowercasedUsernameAndHashedPassword() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("Alice");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.empty());
        when(passwordHasher.hash("correcthorse")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.register(platformId, request);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPlatformId()).isEqualTo(platformId);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.isTotpEnabled()).isFalse();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void checksForExistingUserUsingLowercasedUsername() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("ALICE");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.empty());
        when(passwordHasher.hash("correcthorse")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register(platformId, request);

        verify(userRepository).findByPlatformIdAndUsername(platformId, "alice");
    }

    @Test
    void throwsUsernameTakenWhenUsernameAlreadyExistsForPlatform() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        User existing = new User();
        existing.setId(UUID.randomUUID());
        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.register(platformId, request))
                .isInstanceOf(UsernameTakenException.class);

        verify(userRepository, never()).save(any());
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void treatsMixedCaseDuplicateAsAlreadyTaken() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("ALICE");
        request.setPassword("correcthorse");

        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setUsername("alice");
        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.register(platformId, request))
                .isInstanceOf(UsernameTakenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void generatesUniqueIdForEachRegisteredUser() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO();
        request.setUsername("alice");
        request.setPassword("correcthorse");

        when(userRepository.findByPlatformIdAndUsername(platformId, "alice")).thenReturn(Optional.empty());
        when(passwordHasher.hash("correcthorse")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        userService.register(platformId, request);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getId()).isNotNull();
    }
}
