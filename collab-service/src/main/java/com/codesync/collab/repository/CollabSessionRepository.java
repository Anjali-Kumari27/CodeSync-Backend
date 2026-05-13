package com.codesync.collab.repository;

import com.codesync.collab.model.CollabSession;
import com.codesync.collab.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CollabSession table. Handles all DB operations related to
 * collaboration sessions.
 */
@Repository
public interface CollabSessionRepository extends JpaRepository<CollabSession, Long> {

	/**
	 * Find latest active session for a file.
	 */
	Optional<CollabSession> findFirstByFileIdAndStatusOrderByCreatedAtDesc(Long fileId, SessionStatus status);

	/**
	 * Find session by fileId + status. Added because service/test is calling this
	 * method.
	 */
	Optional<CollabSession> findByFileIdAndStatus(Long fileId, SessionStatus status);

	/**
	 * Get all sessions of a file.
	 */
	List<CollabSession> findByFileIdOrderByCreatedAtDesc(Long fileId);

	/**
	 * Get all sessions in a project by status.
	 */
	List<CollabSession> findByProjectIdAndStatus(Long projectId, SessionStatus status);

	/**
	 * Count sessions in a project by status.
	 */
	long countByProjectIdAndStatus(Long projectId, SessionStatus status);

	/**
	 * Find sessions created by a user.
	 */
	List<CollabSession> findByCreatedByAndStatus(Long createdBy, SessionStatus status);

	/**
	 * Check if session exists for a file.
	 */
	boolean existsByFileIdAndStatus(Long fileId, SessionStatus status);

	/**
	 * Get all sessions by status.
	 */
	List<CollabSession> findByStatus(SessionStatus status);
}