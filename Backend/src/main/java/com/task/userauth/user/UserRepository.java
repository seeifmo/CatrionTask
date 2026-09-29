package com.task.userauth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Usernames and emails are stored lower-case, so these lookups are effectively case-insensitive. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
