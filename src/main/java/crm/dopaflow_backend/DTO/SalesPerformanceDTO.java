package crm.dopaflow_backend.DTO;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
@Getter
@Setter
@Data
public class SalesPerformanceDTO {
    private String name; // Username or email
    private BigDecimal target; // Mocked target
    private BigDecimal achieved; // Total value of WON opportunities for the user
    private double progress; // Percentage of target achieved


}