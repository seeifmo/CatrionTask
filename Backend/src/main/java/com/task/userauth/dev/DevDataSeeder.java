package com.task.userauth.dev;

import com.task.userauth.user.User;
import com.task.userauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the test accounts from application-dev.yml. Runs in the dev profile only. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         SeedProperties seedProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedProperties = seedProperties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (SeedProperties.SeedUser seed : seedProperties.users()) {
            if (userRepository.existsByUsername(seed.username())) {
                continue;
            }
            userRepository.save(new User(seed.username(), seed.email(), seed.fullName(),
                    passwordEncoder.encode(seed.password()), seed.role()));
            log.info("Seeded dev user '{}' with role {}", seed.username(), seed.role());
        }
    }
}
