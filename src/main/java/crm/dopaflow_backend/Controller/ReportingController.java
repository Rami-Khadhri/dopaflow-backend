package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.DTO.KeyIndicatorsDTO;
import crm.dopaflow_backend.DTO.ReportDTO;
import crm.dopaflow_backend.DTO.SalesPerformanceDTO;
import crm.dopaflow_backend.Service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcelReport() {
        try {
            logger.info("Received request for Excel export");
            ReportDTO report = reportingService.generateReport();
            byte[] excelBytes = reportingService.generateExcelReport(report);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "crm-report.xlsx");
            logger.info("Returning Excel report");
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (SecurityException e) {
            logger.error("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        } catch (Exception e) {
            logger.error("Unexpected error during Excel export: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdfReport() {
        try {
            logger.info("Received request for PDF export");
            ReportDTO report = reportingService.generateReport();
            byte[] pdfBytes = reportingService.generatePdfReport(report);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "crm-report-" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE) + ".pdf");
            logger.info("Returning PDF report");
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (SecurityException e) {
            logger.error("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        } catch (Exception e) {
            logger.error("Unexpected error during PDF export: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/history/user/{userId}")
    @PreAuthorize("hasAnyRole('Admin', 'SuperAdmin') or #userId == authentication.principal.id")
    public ResponseEntity<List<SalesPerformanceDTO>> getUserHistory(@PathVariable Long userId) {
        try {
            List<SalesPerformanceDTO> history = reportingService.getUserHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error fetching user history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/history/team/{year}/{month}")
    @PreAuthorize("hasAnyRole('Admin', 'SuperAdmin')")
    public ResponseEntity<List<SalesPerformanceDTO>> getTeamHistory(
            @PathVariable int year,
            @PathVariable int month) {
        try {
            List<SalesPerformanceDTO> teamPerformance = reportingService.getTeamPerformanceForMonth(year, month);
            return ResponseEntity.ok(teamPerformance);
        } catch (Exception e) {
            logger.error("Error fetching team history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/set-global-target")
    @PreAuthorize("hasRole('SuperAdmin')")
    public ResponseEntity<Void> setGlobalTarget(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam BigDecimal target) {
        try {
            logger.info("Setting global target for year: {}, month: {}, target: {}", year, month, target);
            reportingService.setGlobalTarget(year, month, target);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error setting global target: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    private ReportDTO createEmptyReportDTO() {
        ReportDTO errorResponse = new ReportDTO();
        KeyIndicatorsDTO errorIndicators = new KeyIndicatorsDTO();
        errorIndicators.setTotalOpportunityValue(BigDecimal.ZERO);
        errorIndicators.setNewOpportunities(0L);
        errorIndicators.setCompletedTasks(0L);
        errorIndicators.setTotalOpportunities(0L);
        errorIndicators.setNewCompanies(0L);
        errorIndicators.setNewContacts(0L);
        errorResponse.setKeyIndicators(errorIndicators);
        errorResponse.setSalesEvolution(new ArrayList<>());
        errorResponse.setSalesPerformance(new ArrayList<>());
        return errorResponse;
    }

}