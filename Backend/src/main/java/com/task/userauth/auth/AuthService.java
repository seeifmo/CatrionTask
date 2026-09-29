package com.task.userauth.auth;

import com.task.userauth.common.DuplicateUserException;
import com.task.userauth.security.TokenService;
import com.task.userauth.user.Role;
import com.task.userauth.user.User;
import com.task.userauth.user.UserRepository;
import com.task.userauth.user.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, TokenService tokenService,
                       UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Checks the credentials and returns a signed access token.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException for an unknown user or a
     *         wrong password; both cases look the same to the caller.
     */
    public String login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalize(request.username()), request.password()));
        return tokenService.issue(authentication);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        String email = normalize(request.email());
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException("username", "Username is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("email", "Email is already registered.");
        }
        User user = new User(username, email, request.fullName().trim(),
                passwordEncoder.encode(request.password()), Role.USER);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // Two registrations raced past the checks above; the unique constraint caught it.
            throw new DuplicateUserException(null, "Username or email is already registered.");
        }
    }

    /** Usernames and emails are stored lower-case, so uniqueness and login are case-insensitive. */
    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
