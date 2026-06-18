package com.studentconnect.service;

import com.studentconnect.entity.PushToken;
import com.studentconnect.entity.User;
import com.studentconnect.repository.PushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

/**
 * Sends push notifications to users via the Expo Push Notification API.
 *
 * Expo's free tier handles delivery to both Android (FCM) and iOS (APNs)
 * without needing separate Firebase/Apple credentials during development.
 *
 * Expo Push API docs: https://docs.expo.dev/push-notifications/sending-notifications/
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final PushTokenRepository pushTokenRepo;
    private final RestTemplate restTemplate;

    public NotificationService(PushTokenRepository pushTokenRepo,
                                RestTemplate restTemplate) {
        this.pushTokenRepo = pushTokenRepo;
        this.restTemplate  = restTemplate;
    }

    // ── Notification type constants ───────────────────────────────────────────
    public static final String TYPE_JOB_APPLICATION    = "JOB_APPLICATION";
    public static final String TYPE_APPLICATION_UPDATE = "APPLICATION_UPDATE";
    public static final String TYPE_MENTORSHIP_REQUEST = "MENTORSHIP_REQUEST";
    public static final String TYPE_MENTORSHIP_ACCEPTED= "MENTORSHIP_ACCEPTED";
    public static final String TYPE_NEW_MESSAGE        = "NEW_MESSAGE";
    public static final String TYPE_SESSION_REMINDER   = "SESSION_REMINDER";

    /**
     * Send a push notification to all registered devices of a user.
     * Runs asynchronously so it never blocks the HTTP response.
     *
     * @param recipient  The user to notify
     * @param title      Notification title
     * @param body       Notification body text
     * @param type       Notification type constant (for deep-linking in the app)
     * @param extraData  Any extra key-value pairs to include in the data payload
     */
    @Async
    public void sendToUser(User recipient, String title, String body,
                           String type, Map<String, Object> extraData) {

        List<PushToken> tokens = pushTokenRepo.findByUserId(recipient.getId());
        if (tokens.isEmpty()) {
            log.debug("No push tokens registered for user {} — skipping notification.", recipient.getId());
            return;
        }

        for (PushToken pt : tokens) {
            String token = pt.getPushToken();
            if (!token.startsWith("ExponentPushToken")) {
                log.warn("Skipping invalid push token for user {}: {}", recipient.getId(), token);
                continue;
            }
            sendExpoNotification(token, title, body, type, extraData);
        }
    }

    /**
     * Convenience overload without extra data.
     */
    @Async
    public void sendToUser(User recipient, String title, String body, String type) {
        sendToUser(recipient, title, body, type, Map.of());
    }

    // ── Domain-specific notification helpers ─────────────────────────────────

    /**
     * Notify the employer when a student applies for their job.
     */
    @Async
    public void notifyEmployerOfApplication(User employer, String studentName,
                                            String jobTitle, Long jobId, Long applicationId) {
        sendToUser(
            employer,
            "📋 New Application Received",
            studentName + " applied for " + jobTitle,
            TYPE_JOB_APPLICATION,
            Map.of("jobId", jobId, "applicationId", applicationId)
        );
    }

    /**
     * Notify the student when their application status changes.
     */
    @Async
    public void notifyStudentOfApplicationUpdate(User student, String jobTitle,
                                                 String status, Long applicationId) {
        String emoji = switch (status) {
            case "INTERVIEWING" -> "🎉";
            case "HIRED"        -> "🚀";
            case "REJECTED"     -> "📩";
            default             -> "📬";
        };
        sendToUser(
            student,
            emoji + " Application Update",
            "Your application for " + jobTitle + " is now: " + status,
            TYPE_APPLICATION_UPDATE,
            Map.of("applicationId", applicationId, "status", status)
        );
    }

    /**
     * Notify a mentor when a student requests mentorship.
     */
    @Async
    public void notifyMentorOfRequest(User mentor, String studentName,
                                      String topic, Long connectionId) {
        sendToUser(
            mentor,
            "🎓 New Mentorship Request",
            studentName + " wants you as their mentor" +
                (topic != null && !topic.isBlank() ? ": " + topic : ""),
            TYPE_MENTORSHIP_REQUEST,
            Map.of("connectionId", connectionId)
        );
    }

    /**
     * Notify the student when a mentor accepts their request.
     */
    @Async
    public void notifyStudentOfMentorshipAccepted(User student, String mentorName,
                                                   Long connectionId) {
        sendToUser(
            student,
            "✅ Mentorship Accepted!",
            mentorName + " accepted your mentorship request.",
            TYPE_MENTORSHIP_ACCEPTED,
            Map.of("connectionId", connectionId)
        );
    }

    /**
     * Notify a user of a new chat message.
     */
    @Async
    public void notifyNewMessage(User recipient, String senderName, String preview) {
        String truncated = preview.length() > 60
            ? preview.substring(0, 57) + "..."
            : preview;
        sendToUser(
            recipient,
            "💬 " + senderName,
            truncated,
            TYPE_NEW_MESSAGE,
            Map.of("senderName", senderName)
        );
    }

    // ── Internal: call Expo Push API ─────────────────────────────────────────
    private void sendExpoNotification(String expoPushToken, String title,
                                      String body, String type,
                                      Map<String, Object> extraData) {
        try {
            // Build data payload including type for deep-linking
            Map<String, Object> data = new HashMap<>(extraData);
            data.put("type", type);

            // Expo push message format
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("to",         expoPushToken);
            message.put("title",      title);
            message.put("body",       body);
            message.put("data",       data);
            message.put("sound",      "default");
            message.put("priority",   "high");
            message.put("channelId",  "student-connect"); // Android channel

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip, deflate");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                EXPO_PUSH_URL, request, String.class
            );

            log.info("Push notification sent to {} — status: {}", expoPushToken, response.getStatusCode());
        } catch (Exception ex) {
            log.error("Failed to send push notification to {}: {}", expoPushToken, ex.getMessage());
        }
    }
}
