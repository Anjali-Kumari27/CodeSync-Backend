package com.codesync.comment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String oldBody;

    @Column(name = "edited_at", nullable = false)
    @Builder.Default
    private LocalDateTime editedAt = LocalDateTime.now();
    
    @Column(name = "edited_by", nullable = false)
    private Long editedBy;
}
