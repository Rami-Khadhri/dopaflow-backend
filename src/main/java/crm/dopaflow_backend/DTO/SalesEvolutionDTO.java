package crm.dopaflow_backend.DTO;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@Data
public class SalesEvolutionDTO {

    private String month; // Month of the year
    private long completedTasks; // Number of completed tasks
    private BigDecimal opportunityValue; // Total value of WON opportunities

}