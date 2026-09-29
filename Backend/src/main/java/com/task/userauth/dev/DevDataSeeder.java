package com.task.userauth.dev;

import com.task.userauth.user.Role;
import com.task.userauth.user.User;
import com.task.userauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates a test user in the dev profile only. The credentials come from application-dev.yml. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;
    private final String fullName;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         @Value("${app.seed.username}") String username,
                         @Value("${app.seed.password}") String password,
                         @Value("${app.seed.email}") String email,
                         @Value("${app.seed.full-name}") String fullName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(new User(username, email, fullName, passwordEncoder.encode(password), Role.USER));
        log.info("Seeded dev user '{}'", username);
    }
}
