package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Notification;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Security.JwtUtil;
import crm.dopaflow_backend.Service.NotificationService;
import crm.dopaflow_backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            List<Notification> notifications = notificationService.getAllNotifications(user);
            long unreadCount = notificationService.countUnreadNotifications(user);

            List<Map<String, Object>> notificationDto = notifications.stream().map(notification -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", notification.getId());
                map.put("message", notification.getMessage());
                map.put("type", notification.getType().name());
                map.put("timestamp", notification.getTimestamp());
                map.put("isRead", notification.isRead());
                map.put("link", notification.getLink());
                return map;
            }).collect(Collectors.toList());

            return new ResponseEntity<>(Map.of(
                    "notifications", notificationDto,
                    "unreadCount", unreadCount
            ), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token"), HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/unread")
    public ResponseEntity<?> getUnreadNotifications(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            List<Notification> notifications = notificationService.getUnreadNotifications(user);
            long unreadCount = notificationService.countUnreadNotifications(user);

            List<Map<String, Object>> notificationDto = notifications.stream().map(notification -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", notification.getId());
                map.put("message", notification.getMessage());
                map.put("type", notification.getType().name());
                map.put("timestamp", notification.getTimestamp());
                map.put("isRead", notification.isRead());
                map.put("link", notification.getLink());
                return map;
            }).collect(Collectors.toList());

            return new ResponseEntity<>(Map.of(
                    "notifications", notificationDto,
                    "unreadCount", unreadCount
            ), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token"), HttpStatus.UNAUTHORIZED);
        }
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<?> markNotificationAsRead(@RequestHeader("Authorization") String authHeader, @PathVariable Long notificationId) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            notificationService.markNotificationAsRead(notificationId);
            return new ResponseEntity<>(Map.of("message", "Notification marked as read"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token or notification not found"), HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<?> markAllNotificationsAsRead(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            notificationService.markAllNotificationsAsRead(user);
            return new ResponseEntity<>(Map.of("message", "All notifications marked as read"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token"), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<?> deleteNotification(@RequestHeader("Authorization") String authHeader, @PathVariable Long notificationId) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            notificationService.deleteNotification(notificationId);
            return new ResponseEntity<>(Map.of("message", "Notification deleted successfully"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token or notification not found"), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<?> deleteAllNotifications(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
            notificationService.deleteNotificationsForUser(user.getId()); // Pass userId instead of User object
            return new ResponseEntity<>(Map.of("message", "All notifications deleted successfully"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", "Invalid or missing token"), HttpStatus.BAD_REQUEST);
        }
    }
}