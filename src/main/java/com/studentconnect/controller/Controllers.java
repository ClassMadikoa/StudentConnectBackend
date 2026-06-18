package com.studentconnect.controller;

import com.studentconnect.entity.*;
import com.studentconnect.repository.*;
import com.studentconnect.security.JwtUtil;
import com.studentconnect.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// ─── Request / Response DTOs ─────────────────────────────────────────────────
record RegisterRequest(@NotBlank String name, @NotBlank String email,
                       @NotBlank String password, @NotBlank String role) {}
record LoginRequest(@NotBlank String email, @NotBlank String password) {}
record ChatMessageDTO(Long senderId, Long recipientId, String content) {}
record JobRequest(String title, String description, String type,
                  String location, String salary, List<String> requiredSkills) {}
record MentorshipRequest(Long mentorId, String topic) {}

// ─── Auth Controller ──────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final UserRepository userRepo;
    private final StudentProfileRepository studentProfileRepo;
    private final MentorProfileRepository mentorProfileRepo;
    private final EmployerProfileRepository employerProfileRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    AuthController(UserRepository userRepo,
                   StudentProfileRepository studentProfileRepo,
                   MentorProfileRepository mentorProfileRepo,
                   EmployerProfileRepository employerProfileRepo,
                   PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.studentProfileRepo = studentProfileRepo;
        this.mentorProfileRepo = mentorProfileRepo;
        this.employerProfileRepo = employerProfileRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        User.Role role;
        try {
            role = User.Role.valueOf(req.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role. Use STUDENT or EMPLOYER"));
        }

        // Mentor accounts are not self-registrable — they are created by an
        // admin/invite process after vetting (qualifications, references, etc.)
        if (role == User.Role.MENTOR) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Mentor accounts are by invitation only. Please contact support to apply as a mentor."
            ));
        }

        User user = User.builder()
                .name(req.name())
                .email(req.email())
                .passwordHash(encoder.encode(req.password()))
                .role(role)
                .build();
        userRepo.save(user);

        // Create role-specific profile stub
        switch (role) {
            case STUDENT  -> studentProfileRepo.save(StudentProfile.builder().user(user).build());
            case MENTOR   -> mentorProfileRepo.save(MentorProfile.builder().user(user).build());
            case EMPLOYER -> employerProfileRepo.save(EmployerProfile.builder().user(user).build());
        }

        return ResponseEntity.ok(buildAuthResponse(user));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest req) {
        return userRepo.findByEmail(req.email())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .map(u -> ResponseEntity.ok(buildAuthResponse(u)))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid email or password")));
    }

    private Map<String, Object> buildAuthResponse(User user) {
        return Map.of(
                "token", jwtUtil.generateToken(user),
                "user", Map.of(
                        "id",    user.getId(),
                        "name",  user.getName(),
                        "email", user.getEmail(),
                        "role",  user.getRole().name()
                )
        );
    }

    /** PUT /api/auth/change-password */
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String currentPw = body.get("currentPassword");
        String newPw     = body.get("newPassword");

        if (currentPw == null || newPw == null || newPw.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request — new password must be 8+ characters"));
        }

        return userRepo.findByEmail(auth.getName())
                .map(user -> {
                    if (!encoder.matches(currentPw, user.getPasswordHash())) {
                        return ResponseEntity.status(401)
                                .body(Map.of("error", "Current password is incorrect"));
                    }
                    user.setPasswordHash(encoder.encode(newPw));
                    userRepo.save(user);
                    return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }
}

// ─── Student Controller ───────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/students")
class StudentController {

    private final UserRepository userRepo;
    private final StudentProfileRepository profileRepo;
    private final JobListingRepository jobListingRepo;

    StudentController(UserRepository userRepo,
                      StudentProfileRepository profileRepo,
                      JobListingRepository jobListingRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.jobListingRepo = jobListingRepo;
    }

    /** GET /api/students/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getProfile(@PathVariable Long id) {
        return profileRepo.findByUserId(id)
                .map(p -> ResponseEntity.ok(p))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/students/matches
     * Returns job listings matched to the authenticated student's skills via JSONB query.
     */
    @GetMapping("/matches")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getMatches(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .flatMap(u -> profileRepo.findByUserId(u.getId()))
                .map(profile -> {
                    List<String> skills = profile.getSkills();
                    if (skills == null || skills.isEmpty()) {
                        return ResponseEntity.ok(jobListingRepo.findByActiveTrue());
                    }
                    // Build JSON array string for native query
                    String skillsJson = "[" + String.join(",",
                            skills.stream().map(s -> "\"" + s + "\"").toList()) + "]";
                    return ResponseEntity.ok(jobListingRepo.findMatchingJobsForStudent(skillsJson));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** PUT /api/students/{id} — update profile */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        return profileRepo.findByUserId(id).map(profile -> {
            if (body.containsKey("major"))    profile.setMajor((String) body.get("major"));
            if (body.containsKey("bio"))      profile.setBio((String) body.get("bio"));
            if (body.containsKey("skills"))   profile.setSkills((List<String>) body.get("skills"));
            profileRepo.save(profile);
            return ResponseEntity.ok(profile);
        }).orElse(ResponseEntity.notFound().build());
    }
}

// ─── Job Controller ───────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/jobs")
class JobController {

    private final JobListingRepository jobRepo;
    private final JobApplicationRepository applicationRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    JobController(JobListingRepository jobRepo,
                  JobApplicationRepository applicationRepo,
                  UserRepository userRepo,
                  NotificationService notificationService) {
        this.jobRepo             = jobRepo;
        this.applicationRepo     = applicationRepo;
        this.userRepo            = userRepo;
        this.notificationService = notificationService;
    }

    /** GET /api/jobs — public */
    @GetMapping
    public ResponseEntity<List<JobListing>> getAll() {
        return ResponseEntity.ok(jobRepo.findByActiveTrue());
    }

    /** GET /api/jobs/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return jobRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/jobs — employer only */
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<?> postJob(@RequestBody JobRequest req, Authentication auth) {
        User employer = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        JobListing.JobType type;
        try {
            type = JobListing.JobType.valueOf(req.type().toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid job type"));
        }
        JobListing listing = JobListing.builder()
                .employer(employer).title(req.title()).description(req.description())
                .type(type).location(req.location()).salary(req.salary())
                .requiredSkills(req.requiredSkills()).active(true).build();
        return ResponseEntity.ok(jobRepo.save(listing));
    }

    /**
     * POST /api/jobs/{id}/apply — student only
     * Saves the application then fires an async push notification to the employer.
     */
    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> apply(@PathVariable Long id, Authentication auth) {
        User student = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (applicationRepo.existsByJobIdAndStudentId(id, student.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already applied"));
        }

        JobListing job = jobRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        JobApplication saved = applicationRepo.save(
            JobApplication.builder()
                .job(job).student(student)
                .status(JobApplication.ApplicationStatus.SUBMITTED)
                .build()
        );

        // Notify the employer — runs async, never blocks the response
        notificationService.notifyEmployerOfApplication(
            job.getEmployer(), student.getName(), job.getTitle(),
            job.getId(), saved.getId()
        );

        return ResponseEntity.ok(saved);
    }

    /**
     * PATCH /api/jobs/applications/{id}/status — employer moves application through pipeline.
     * Notifies the student of the status change.
     */
    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        return applicationRepo.findById(id).map(app -> {
            try {
                app.setStatus(JobApplication.ApplicationStatus.valueOf(newStatus));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
            }
            applicationRepo.save(app);
            notificationService.notifyStudentOfApplicationUpdate(
                app.getStudent(), app.getJob().getTitle(), newStatus, app.getId()
            );
            return ResponseEntity.ok(app);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/jobs/applications — student sees their own applications */
    @GetMapping("/applications")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getMyApplications(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .map(u -> ResponseEntity.ok(applicationRepo.findByStudentId(u.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/jobs/{id}/applicants — employer sees who applied */
    @GetMapping("/{id}/applicants")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<?> getApplicants(@PathVariable Long id) {
        return ResponseEntity.ok(applicationRepo.findByJobId(id));
    }
}

// ─── Mentorship Controller ────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/mentorship")
class MentorshipController {

    private final MentorshipConnectionRepository connectionRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    MentorshipController(MentorshipConnectionRepository connectionRepo,
                         UserRepository userRepo,
                         NotificationService notificationService) {
        this.connectionRepo      = connectionRepo;
        this.userRepo            = userRepo;
        this.notificationService = notificationService;
    }

    /**
     * POST /api/mentorship/request — student sends request.
     * Notifies the mentor immediately via push.
     */
    @PostMapping("/request")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> request(@RequestBody MentorshipRequest req, Authentication auth) {
        User student = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        User mentor = userRepo.findById(req.mentorId())
                .orElseThrow(() -> new RuntimeException("Mentor not found"));

        if (connectionRepo.findByStudentIdAndMentorId(student.getId(), mentor.getId()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request already exists"));
        }

        MentorshipConnection conn = MentorshipConnection.builder()
                .student(student)
                .mentor(mentor)
                .topic(req.topic())
                .status(MentorshipConnection.ConnectionStatus.PENDING)
                .build();
        MentorshipConnection saved = connectionRepo.save(conn);

        // Notify the mentor — async, non-blocking
        notificationService.notifyMentorOfRequest(
            mentor, student.getName(), req.topic(), saved.getId()
        );

        return ResponseEntity.ok(saved);
    }

    /**
     * PUT /api/mentorship/{id}/approve — mentor accepts.
     * Notifies the student their request was accepted.
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        User mentor = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return connectionRepo.findById(id).map(conn -> {
            conn.setStatus(MentorshipConnection.ConnectionStatus.ACTIVE);
            conn.setAcceptedAt(LocalDateTime.now());
            connectionRepo.save(conn);

            // Notify the student — async
            notificationService.notifyStudentOfMentorshipAccepted(
                conn.getStudent(), mentor.getName(), conn.getId()
            );

            return ResponseEntity.ok(conn);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/mentorship/{id}/decline — mentor declines.
     */
    @PutMapping("/{id}/decline")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<?> decline(@PathVariable Long id) {
        return connectionRepo.findById(id).map(conn -> {
            conn.setStatus(MentorshipConnection.ConnectionStatus.TERMINATED);
            connectionRepo.save(conn);
            return ResponseEntity.ok(Map.of("message", "Request declined"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/mentorship/mentees — mentor views their mentees */
    @GetMapping("/mentees")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<?> getMentees(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .map(u -> ResponseEntity.ok(connectionRepo.findByMentorId(u.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/mentorship/sessions — student's connections */
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getSessions(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .map(u -> ResponseEntity.ok(connectionRepo.findByStudentId(u.getId())))
                .orElse(ResponseEntity.notFound().build());
    }
}

// ─── Chat Controller (STOMP WebSocket) ────────────────────────────────────────
@org.springframework.stereotype.Controller
class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;
    private final MentorshipConnectionRepository connectionRepo;
    private final NotificationService notificationService;

    ChatController(SimpMessagingTemplate messagingTemplate,
                   MessageRepository messageRepo,
                   UserRepository userRepo,
                   MentorshipConnectionRepository connectionRepo,
                   NotificationService notificationService) {
        this.messagingTemplate   = messagingTemplate;
        this.messageRepo         = messageRepo;
        this.userRepo            = userRepo;
        this.connectionRepo      = connectionRepo;
        this.notificationService = notificationService;
    }

    /**
     * React Native STOMP client sends to: /app/chat.sendMessage
     * Payload: { senderId, recipientId, content }
     *
     * Flow:
     * 1. Persist message to PostgreSQL
     * 2. Forward via STOMP to recipient's queue (if online)
     * 3. Send push notification (if offline — Expo handles this gracefully)
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDTO dto) {
        User sender = userRepo.findById(dto.senderId())
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User recipient = userRepo.findById(dto.recipientId())
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        Message saved = messageRepo.save(Message.builder()
                .sender(sender)
                .recipient(recipient)
                .content(dto.content())
                .build());

        // Deliver via STOMP to recipient's personal queue (real-time if online)
        messagingTemplate.convertAndSendToUser(
                String.valueOf(dto.recipientId()),
                "/queue/messages",
                Map.of(
                        "id",          saved.getId(),
                        "senderId",    dto.senderId(),
                        "recipientId", dto.recipientId(),
                        "content",     dto.content(),
                        "sentAt",      saved.getSentAt().toString()
                )
        );

        // Also send push notification — recipient will see it if their app is backgrounded
        notificationService.notifyNewMessage(recipient, sender.getName(), dto.content());
    }
}

// ─── Messages REST Controller ─────────────────────────────────────────────────
@RestController
@RequestMapping("/api/messages")
class MessageRestController {

    private final MessageRepository messageRepo;
    private final UserRepository userRepo;

    MessageRestController(MessageRepository messageRepo, UserRepository userRepo) {
        this.messageRepo = messageRepo;
        this.userRepo = userRepo;
    }

    /** GET /api/messages/conversations — conversation list */
    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getConversations(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .map(u -> ResponseEntity.ok(messageRepo.findConversationsForUser(u.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/messages/{userId} — full chat thread with a specific user */
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getChat(@PathVariable Long userId, Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .map(u -> ResponseEntity.ok(messageRepo.findChatBetween(u.getId(), userId)))
                .orElse(ResponseEntity.notFound().build());
    }
}
