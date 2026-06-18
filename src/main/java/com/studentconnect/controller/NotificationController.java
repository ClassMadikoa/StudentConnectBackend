package com.studentconnect.controller;

import com.studentconnect.entity.PushToken;
import com.studentconnect.repository.PushTokenRepository;
import com.studentconnect.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final PushTokenRepository pushTokenRepo;
    private final UserRepository userRepo;

    public NotificationController(PushTokenRepository pushTokenRepo,
                                   UserRepository userRepo) {
        this.pushTokenRepo = pushTokenRepo;
        this.userRepo      = userRepo;
    }

    /**
     * POST /api/notifications/register-token
     * Called by the React Native app on startup after push permission is granted.
     * Stores the Expo push token so the backend can deliver notifications to this device.
     */
    @PostMapping("/register-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> registerToken(@RequestBody Map<String, String> body,
                                            Authentication auth) {
        String pushToken = body.get("pushToken");
        if (pushToken == null || pushToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "pushToken is required"));
        }

        return userRepo.findByEmail(auth.getName()).map(user -> {
            // Only store if not already registered for this user+token pair
            if (pushTokenRepo.findByUserIdAndPushToken(user.getId(), pushToken).isEmpty()) {
                PushToken pt = PushToken.builder()
                        .user(user)
                        .pushToken(pushToken)
                        .build();
                pushTokenRepo.save(pt);
            }
            return ResponseEntity.ok(Map.of("message", "Push token registered"));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    /**
     * DELETE /api/notifications/register-token
     * Called on logout to stop notifications being sent to this device.
     */
    @DeleteMapping("/register-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> unregisterToken(@RequestBody Map<String, String> body,
                                              Authentication auth) {
        String pushToken = body.get("pushToken");
        return userRepo.findByEmail(auth.getName()).map(user -> {
            pushTokenRepo.deleteByUserIdAndPushToken(user.getId(), pushToken);
            return ResponseEntity.ok(Map.of("message", "Push token unregistered"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
