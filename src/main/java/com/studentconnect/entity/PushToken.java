package com.studentconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "push_tokens",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "push_token"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "push_token", nullable = false)
    private String pushToken;

    private LocalDateTime registeredAt;

    @PrePersist
    protected void onCreate() { registeredAt = LocalDateTime.now(); }
}
