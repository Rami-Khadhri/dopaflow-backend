package crm.dopaflow_backend.DTO;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Data
public class ReportDTO {
    // Getters and setters
    private KeyIndicatorsDTO keyIndicators;
    private List<SalesEvolutionDTO> salesEvolution;
    private List<SalesPerformanceDTO> salesPerformance;

}