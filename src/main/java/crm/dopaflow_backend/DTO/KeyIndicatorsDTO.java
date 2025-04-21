package crm.dopaflow_backend.DTO;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Data
public class KeyIndicatorsDTO {
    private BigDecimal totalOpportunityValue; // Total value of WON opportunities
    private long newOpportunities; // Count of IN_PROGRESS opportunities
    private long completedTasks; // Count of Done tasks
    private double customerSatisfaction; // Mocked value
    private long newCompanies;
    private long newContacts;

}