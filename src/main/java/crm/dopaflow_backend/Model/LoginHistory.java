// LoginHistory.java (Updated)
package crm.dopaflow_backend.Model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "login_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude // Prevent circular reference in toString
    private User user;

    @Column(nullable = false)
    private String ipAddress;

    @Column
    private String location;

    @Column
    private String deviceInfo;

    @Column(nullable = false)
    private Date loginTime;
}