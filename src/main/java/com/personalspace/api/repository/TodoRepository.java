package com.personalspace.api.repository;

import com.personalspace.api.model.entity.Todo;
import com.personalspace.api.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TodoRepository extends JpaRepository<Todo, UUID> {
    Optional<Todo> findByIdAndUser(UUID id, User user);

    Page<Todo> findByUserAndArchived(User user, boolean archived, Pageable pageable);

    @Query("""
        SELECT t
        FROM Todo t
        WHERE t.user = :user
          AND t.archived = :archived
          AND LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<Todo> searchByUserAndQuery(
        @Param("user") User user,
        @Param("query") String query,
        @Param("archived") boolean archived,
        Pageable pageable
    );
}