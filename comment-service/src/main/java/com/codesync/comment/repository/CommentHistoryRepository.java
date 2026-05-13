package com.codesync.comment.repository;

import com.codesync.comment.model.CommentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentHistoryRepository extends JpaRepository<CommentHistory, Long> {
}
