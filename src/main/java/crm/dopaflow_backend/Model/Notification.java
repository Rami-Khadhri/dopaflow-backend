package crm.dopaflow_backend.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 5000)
    private String message;

    @Column(nullable = false)
    private NotificationType type; // Enum for notification types (e.g., PASSWORD_CHANGE, TWO_FA_ENABLED, TWO_FA_DISABLED)

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private String link; // Added
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;

    public Notification() {

    }

    public Notification(User user, String message, NotificationType type, boolean isRead, String link, LocalDateTime timestamp) {
        this.user = user;
        this.message = message;
        this.type = type;
        this.isRead = isRead;
        this.link = link;
        this.timestamp = timestamp;
    }

    // Enum for notification types
    public enum NotificationType {
        PASSWORD_CHANGE,
        TWO_FA_ENABLED,
        TWO_FA_DISABLED,
        // Add more types as needed for future use cases
        USER_CREATED,
        USER_DELETED,
        CONTACT_CREATED,
        TASK_ASSIGNED,
        MESSAGE_RECEIVED,
        TICKET_OPENED,
        TICKET_CLOSED,
        TICKET_STATUS_CHANGED,
        TASK_OVERDUE,
        TASK_UPCOMING // New type for 24-hour reminders

    }
}