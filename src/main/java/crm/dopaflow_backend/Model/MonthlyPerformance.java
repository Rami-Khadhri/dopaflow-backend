package crm.dopaflow_backend.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Entity
public class MonthlyPerformance {

    // Getters and Setters
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    private int year;
    @Setter
    private int month; // 1-12
    private BigDecimal target = BigDecimal.valueOf(3000); // Updated default to 3000

    // Constructors
    public MonthlyPerformance() {}

    public MonthlyPerformance(User user, int year, int month, BigDecimal target) {
        this.user = user;
        this.year = year;
        this.month = month;
        this.target = target != null ? target : BigDecimal.valueOf(3000);
    }

    public void setTarget(BigDecimal target) { this.target = target != null ? target : BigDecimal.valueOf(3000); }
}