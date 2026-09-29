package com.task.userauth.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the Flyway schema (ddl-auto=validate), so it also checks entity and migration agree. */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsByUsername() {
        userRepository.saveAndFlush(new User("bob", "bob@example.com", "Bob", "{bcrypt}x", Role.USER));

        assertThat(userRepository.findByUsername("bob"))
                .hasValueSatisfying(user -> {
                    assertThat(user.getId()).isNotNull();
                    assertThat(user.getCreatedAt()).isNotNull();
                });
        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }

    @Test
    void usernameMustBeUnique() {
        userRepository.saveAndFlush(new User("carol", "carol@example.com", "Carol", "{bcrypt}x", Role.USER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("carol", "other@example.com", "Carol 2", "{bcrypt}x", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailMustBeUnique() {
        userRepository.saveAndFlush(new User("dave", "dave@example.com", "Dave", "{bcrypt}x", Role.USER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("dave2", "dave@example.com", "Dave 2", "{bcrypt}x", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
