package com.scalecart.user;

import com.scalecart.common.exceptions.DuplicateResourceException;
import com.scalecart.config.JwtTokenProvider;
import com.scalecart.user.dto.AuthResponse;
import com.scalecart.user.dto.LoginRequest;
import com.scalecart.user.dto.RegisterRequest;
import com.scalecart.user.model.Role;
import com.scalecart.user.model.User;
import com.scalecart.user.repository.UserRepository;
import com.scalecart.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded_pass")
                .firstName("Test")
                .lastName("User")
                .role(Role.ROLE_CUSTOMER)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new user and return JWT token")
    void testRegisterSuccess() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password123")
                .firstName("Test")
                .lastName("User")
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(tokenProvider.generateTokenFromUser(any(User.class))).thenReturn("mock_jwt_token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock_jwt_token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_CUSTOMER);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email is already registered")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password123")
                .firstName("Test")
                .lastName("User")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should authenticate user and return token on valid login")
    void testLoginSuccess() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("Password123")
                .build();

        Authentication mockAuth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(tokenProvider.generateToken(mockAuth, sampleUser)).thenReturn("mock_jwt_token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock_jwt_token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
    }
}
