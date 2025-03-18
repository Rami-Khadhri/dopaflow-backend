package crm.dopaflow_backend.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "chat_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String sender; // "user" or "ai"

    @Column(nullable = false, length = 20000)
    private String text;

    @Column(nullable = false)
    private Date timestamp;

    public ChatHistory(User user, String sender, String text) {
        this.user = user;
        this.sender = sender;
        this.text = text.substring(0, Math.min(20000, text.length())); // Cap at 20k chars
        this.timestamp = new Date();
    }
}