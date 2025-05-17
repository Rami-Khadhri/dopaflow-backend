package crm.dopaflow_backend.DTO;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesPerformanceDTO {
    private String name;
    private BigDecimal target;
    private BigDecimal achieved;
    private Double progress;
    private Long userId; // Added
    private String monthYear; // Added for history (e.g., "January 2025")
}