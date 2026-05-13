package com.codesync.auth.repository;

import com.codesync.auth.model.User;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Query("""
        SELECT u FROM User u
        WHERE u.enabled = true AND (
            LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
        )
    """)
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);

    Page<User> findAllByOrderByIdDesc(Pageable pageable);

    long countByEnabled(boolean enabled);
}