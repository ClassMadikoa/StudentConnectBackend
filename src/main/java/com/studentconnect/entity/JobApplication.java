package com.studentconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// ─── Job Application ──────────────────────────────────────────────────────────
@Entity
@Table(name = "job_applications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "student_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobApplication {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "job_id", nullable = false)
    private JobListing job;

    @ManyToOne @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    private LocalDateTime appliedAt;

    @PrePersist protected void onCreate() { appliedAt = LocalDateTime.now(); }

    public enum ApplicationStatus { SUBMITTED, INTERVIEWING, HIRED, REJECTED }
}
