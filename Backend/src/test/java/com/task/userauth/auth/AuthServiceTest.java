package com.task.userauth.auth;

import com.task.userauth.common.DuplicateUserException;
import com.task.userauth.security.TokenService;
import com.task.userauth.user.Role;
import com.task.userauth.user.User;
import com.task.userauth.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final RegisterRequest request =
            new RegisterRequest("  Alice ", "Alice@Example.COM", " Alice Smith ", "Passw0rd!");

    @Test
    void registerNormalizesInputAndHashesPassword() {
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("{bcrypt}hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Alice Smith");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsTakenUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUserException.class)
                .extracting("field").isEqualTo("username");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsTakenEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUserException.class)
                .extracting("field").isEqualTo("email");
    }

    @Test
    void registerTranslatesConstraintViolationFromConcurrentInsert() {
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateUserException.class);
    }
}
