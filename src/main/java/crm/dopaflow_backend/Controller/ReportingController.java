package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.DTO.KeyIndicatorsDTO;
import crm.dopaflow_backend.DTO.ReportDTO;
import crm.dopaflow_backend.Service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
public class ReportingController {

    private static final Logger logger = LoggerFactory.getLogger(ReportingController.class);

    private final ReportingService reportingService;

    @GetMapping
    public ResponseEntity<ReportDTO> getReport() {
        try {
            logger.info("Received request for report");
            ReportDTO report = reportingService.generateReport();
            logger.info("Returning report data");
            return ResponseEntity.ok(report);
        } catch (SecurityException e) {
            logger.error("Authentication error: {}", e.getMessage());
            ReportDTO errorResponse = createEmptyReportDTO();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            ReportDTO errorResponse = createEmptyReportDTO();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private ReportDTO createEmptyReportDTO() {
        ReportDTO errorResponse = new ReportDTO();
        KeyIndicatorsDTO errorIndicators = new KeyIndicatorsDTO();
        errorIndicators.setTotalOpportunityValue(BigDecimal.ZERO);
        errorIndicators.setNewOpportunities(0L);
        errorIndicators.setCompletedTasks(0L);
        errorIndicators.setCustomerSatisfaction(0.0);
        errorIndicators.setNewCompanies(0L);
        errorIndicators.setNewContacts(0L);
        errorResponse.setKeyIndicators(errorIndicators);
        errorResponse.setSalesEvolution(new ArrayList<>());
        errorResponse.setSalesPerformance(new ArrayList<>());
        return errorResponse;
    }
}