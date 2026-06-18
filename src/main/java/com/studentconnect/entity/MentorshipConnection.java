package com.studentconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// ─── Mentorship Connection ────────────────────────────────────────────────────
@Entity
@Table(name = "mentorship_connections",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "mentor_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MentorshipConnection {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @Enumerated(EnumType.STRING)
    private ConnectionStatus status = ConnectionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String topic;

    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;

    @PrePersist protected void onCreate() { requestedAt = LocalDateTime.now(); }

    public enum ConnectionStatus { PENDING, ACTIVE, TERMINATED }
}
