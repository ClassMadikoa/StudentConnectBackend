package com.studentconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// ─── Message ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "messages")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private LocalDateTime sentAt;
    private Boolean read = false;

    @PrePersist protected void onCreate() { sentAt = LocalDateTime.now(); }
}
