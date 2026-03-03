package com.personalspace.api.repository;

import com.personalspace.api.model.entity.User;
import com.personalspace.api.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@test.com");
        user.setPassword("encodedPassword");
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    @Test
    void findByEmail_shouldReturnUserWhenExists() {
        Optional<User> found = userRepository.findByEmail("test@test.com");
        assertTrue(found.isPresent());
        assertEquals("Test User", found.get().getName());
    }

    @Test
    void findByEmail_shouldReturnEmptyWhenNotExists() {
        Optional<User> found = userRepository.findByEmail("nonexistent@test.com");
        assertFalse(found.isPresent());
    }

    @Test
    void existsByEmail_shouldReturnTrueWhenExists() {
        assertTrue(userRepository.existsByEmail("test@test.com"));
    }

    @Test
    void existsByEmail_shouldReturnFalseWhenNotExists() {
        assertFalse(userRepository.existsByEmail("nonexistent@test.com"));
    }
}
