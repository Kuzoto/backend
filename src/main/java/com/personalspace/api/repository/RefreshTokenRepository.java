package com.personalspace.api.repository;

import com.personalspace.api.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser_Id(UUID userId);
    void deleteByToken(String token);
}
