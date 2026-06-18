package com.studentconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;

// ─── Job Listing ──────────────────────────────────────────────────────────────
@Entity
@Table(name = "job_listings")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobListing {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employer_id", nullable = false)
    private User employer;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private JobType type;

    private String location;
    private String salary;

    // PostgreSQL JSONB – enables @> containment queries for skill matching
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> requiredSkills;

    private Boolean active = true;
    private LocalDateTime postedAt;

    @PrePersist
    protected void onCreate() { postedAt = LocalDateTime.now(); }

    public enum JobType { INTERNSHIP, PART_TIME, FULL_TIME, BURSARY }
}
