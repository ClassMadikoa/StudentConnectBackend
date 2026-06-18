package com.studentconnect.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employer_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    private String companyName;

    private String industry;
    private String website;
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;
}
