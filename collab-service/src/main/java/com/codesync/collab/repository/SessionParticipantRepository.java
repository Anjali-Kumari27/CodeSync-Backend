package com.codesync.collab.repository;

import com.codesync.collab.model.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SessionParticipant}.
 */
@Repository
public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /** All currently active participants (not yet left) in a session. */
    List<SessionParticipant> findBySessionIdAndLeftAtIsNull(Long sessionId);

    /** Check if a specific user is currently in a session. */
    boolean existsBySessionIdAndUserIdAndLeftAtIsNull(Long sessionId, Long userId);

    /** Find the current active participation record for a user in a session. */
    Optional<SessionParticipant> findBySessionIdAndUserIdAndLeftAtIsNull(Long sessionId, Long userId);

    /** Count currently active participants in a session. */
    long countBySessionIdAndLeftAtIsNull(Long sessionId);

    /** All participation records for a session (history). */
    List<SessionParticipant> findBySessionId(Long sessionId);
}
