package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.DTO.KeyIndicatorsDTO;
import crm.dopaflow_backend.DTO.ReportDTO;
import crm.dopaflow_backend.DTO.SalesEvolutionDTO;
import crm.dopaflow_backend.DTO.SalesPerformanceDTO;
import crm.dopaflow_backend.Model.Opportunity;
import crm.dopaflow_backend.Model.StatutOpportunity;
import crm.dopaflow_backend.Model.StatutTask;
import crm.dopaflow_backend.Model.Task;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Repository.CompanyRepository;
import crm.dopaflow_backend.Repository.ContactRepository;
import crm.dopaflow_backend.Repository.OpportunityRepository;
import crm.dopaflow_backend.Repository.TaskRepository;
import crm.dopaflow_backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM");

    private final OpportunityRepository opportunityRepository;
    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ReportDTO generateReport() {
        ReportDTO report = new ReportDTO();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixMonthsAgo = now.minus(6, ChronoUnit.MONTHS);
        LocalDateTime oneMonthAgo = now.minus(1, ChronoUnit.MONTHS);
        Date sixMonthsAgoDate = Date.from(sixMonthsAgo.atZone(ZoneId.systemDefault()).toInstant());

        User currentUser = getCurrentUser();

        // Key Indicators
        KeyIndicatorsDTO keyIndicators = new KeyIndicatorsDTO();
        Double totalOpportunityValue = opportunityRepository.getTotalOpportunityValue(StatutOpportunity.WON);
        keyIndicators.setTotalOpportunityValue(totalOpportunityValue != null ? BigDecimal.valueOf(totalOpportunityValue) : BigDecimal.ZERO);
        keyIndicators.setNewOpportunities(opportunityRepository.findWonOpportunitiesSince(StatutOpportunity.IN_PROGRESS, oneMonthAgo).size());
        keyIndicators.setCompletedTasks(taskRepository.countCompletedTasks(StatutTask.Done));
        keyIndicators.setTotalOpportunities(opportunityRepository.countOpportunities());
        keyIndicators.setNewCompanies(companyRepository.countNewCompaniesSince(oneMonthAgo));
        keyIndicators.setNewContacts(contactRepository.countNewContactsSince(oneMonthAgo));
        report.setKeyIndicators(keyIndicators);

        // Sales Evolution
        List<Task> completedTasks = taskRepository.findCompletedTasksSince(StatutTask.Done, sixMonthsAgoDate);
        List<Opportunity> wonOpportunities = opportunityRepository.findWonOpportunitiesSince(StatutOpportunity.WON, sixMonthsAgo);
        List<SalesEvolutionDTO> salesEvolution = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minus(i, ChronoUnit.MONTHS).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            SalesEvolutionDTO monthData = new SalesEvolutionDTO();
            monthData.setMonth(monthStart.format(MONTH_FORMATTER).toUpperCase());
            monthData.setCompletedTasks(0L);
            monthData.setOpportunityValue(BigDecimal.ZERO);
            salesEvolution.add(monthData);
        }

        for (Task task : completedTasks) {
            if (task.getDeadline() != null) {
                LocalDateTime deadline = LocalDateTime.ofInstant(task.getDeadline().toInstant(), ZoneId.systemDefault());
                if (!deadline.isBefore(sixMonthsAgo)) {
                    String month = deadline.format(MONTH_FORMATTER).toUpperCase();
                    salesEvolution.stream()
                            .filter(data -> data.getMonth().equals(month))
                            .findFirst()
                            .ifPresent(data -> data.setCompletedTasks(data.getCompletedTasks() + 1));
                }
            }
        }

        for (Opportunity op : wonOpportunities) {
            LocalDateTime createdDate = op.getCreatedAt();
            if (createdDate != null && !createdDate.isBefore(sixMonthsAgo)) {
                String month = createdDate.format(MONTH_FORMATTER).toUpperCase();
                salesEvolution.stream()
                        .filter(data -> data.getMonth().equals(month))
                        .findFirst()
                        .ifPresent(data -> data.setOpportunityValue(data.getOpportunityValue().add(op.getValue() != null ? op.getValue() : BigDecimal.ZERO)));
            }
        }
        report.setSalesEvolution(salesEvolution);

        // Sales Performance
        List<SalesPerformanceDTO> salesPerformance = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            List<Opportunity> userOpportunities = opportunityRepository.findWonOpportunitiesSinceForUser(StatutOpportunity.WON, sixMonthsAgo, user.getId());
            BigDecimal achieved = userOpportunities.stream()
                    .map(op -> op.getValue() != null ? op.getValue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = new BigDecimal("3000.0");

            SalesPerformanceDTO performance = new SalesPerformanceDTO();
            performance.setName(user.getUsername() != null ? user.getUsername() : user.getEmail());
            performance.setTarget(target);
            performance.setAchieved(achieved);
            performance.setProgress(target.compareTo(BigDecimal.ZERO) != 0 ? achieved.divide(target, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0);
            salesPerformance.add(performance);
        }
        report.setSalesPerformance(salesPerformance);

        return report;
    }

    public byte[] generateExcelReport(ReportDTO report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CRM Report");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("Key Indicators");
            headerCell.setCellStyle(headerStyle);

            KeyIndicatorsDTO indicators = report.getKeyIndicators();
            createRow(sheet, rowNum++, "Total Opportunity Value (TND)", indicators.getTotalOpportunityValue().toString());
            createRow(sheet, rowNum++, "New Opportunities", String.valueOf(indicators.getNewOpportunities()));
            createRow(sheet, rowNum++, "Completed Tasks", String.valueOf(indicators.getCompletedTasks()));
            createRow(sheet, rowNum++, "Total Opportunities", String.valueOf(indicators.getTotalOpportunities()));
            createRow(sheet, rowNum++, "New Companies", String.valueOf(indicators.getNewCompanies()));
            createRow(sheet, rowNum++, "New Contacts", String.valueOf(indicators.getNewContacts()));

            rowNum++;
            headerRow = sheet.createRow(rowNum++);
            headerCell = headerRow.createCell(0);
            headerCell.setCellValue("Sales Evolution");
            headerCell.setCellStyle(headerStyle);

            Row evolutionHeader = sheet.createRow(rowNum++);
            String[] evolutionColumns = {"Month", "Completed Tasks", "Opportunity Value (TND)"};
            for (int i = 0; i < evolutionColumns.length; i++) {
                Cell cell = evolutionHeader.createCell(i);
                cell.setCellValue(evolutionColumns[i]);
                cell.setCellStyle(headerStyle);
            }

            for (SalesEvolutionDTO evolution : report.getSalesEvolution()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(evolution.getMonth());
                row.createCell(1).setCellValue(evolution.getCompletedTasks());
                row.createCell(2).setCellValue(evolution.getOpportunityValue().doubleValue());
            }

            rowNum++;
            headerRow = sheet.createRow(rowNum++);
            headerCell  = headerRow.createCell(0);
            headerCell.setCellValue("Sales Performance");
            headerCell.setCellStyle(headerStyle);

            Row performanceHeader = sheet.createRow(rowNum++);
            String[] performanceColumns = {"User", "Target (TND)", "Achieved (TND)", "Progress (%)"};
            for (int i = 0; i < performanceColumns.length; i++) {
                Cell cell = performanceHeader.createCell(i);
                cell.setCellValue(performanceColumns[i]);
                cell.setCellStyle(headerStyle);
            }

            for (SalesPerformanceDTO performance : report.getSalesPerformance()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(performance.getName());
                row.createCell(1).setCellValue(performance.getTarget().doubleValue());
                row.createCell(2).setCellValue(performance.getAchieved().doubleValue());
                row.createCell(3).setCellValue(performance.getProgress());
            }

            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    public byte[] generatePdfReport(ReportDTO report) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Define fonts
                PDType1Font titleFont = PDType1Font.HELVETICA_BOLD;
                PDType1Font headerFont = PDType1Font.HELVETICA_BOLD;
                PDType1Font textFont = PDType1Font.HELVETICA;

                // Title
                contentStream.beginText();
                contentStream.setFont(titleFont, 18);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("CRM Report");
                contentStream.endText();

                // Key Indicators Section
                contentStream.beginText();
                contentStream.setFont(headerFont, 14);
                contentStream.newLineAtOffset(50, 720);
                contentStream.showText("Key Indicators");
                contentStream.endText();

                // Table setup
                float yPosition = 700;
                float tableWidth = 500;
                float margin = 50;
                float rowHeight = 20;
                float cellMargin = 5;

                // Key Indicators Table Headers
                contentStream.setFont(headerFont, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Indicator");
                contentStream.newLineAtOffset(250, 0);
                contentStream.showText("Value");
                contentStream.endText();

                // Draw line under headers
                contentStream.setLineWidth(1f);
                contentStream.moveTo(margin, yPosition - 5);
                contentStream.lineTo(margin + tableWidth, yPosition - 5);
                contentStream.stroke();

                // Key Indicators Data
                contentStream.setFont(textFont, 10);
                KeyIndicatorsDTO indicators = report.getKeyIndicators();
                String[][] indicatorsData = {
                        {"Total Opportunity Value (TND)", indicators.getTotalOpportunityValue().toString()},
                        {"New Opportunities", String.valueOf(indicators.getNewOpportunities())},
                        {"Completed Tasks", String.valueOf(indicators.getCompletedTasks())},
                        {"Total Opportunities", String.valueOf(indicators.getTotalOpportunities())},
                        {"New Companies", String.valueOf(indicators.getNewCompanies())},
                        {"New Contacts", String.valueOf(indicators.getNewContacts())}
                };

                yPosition -= rowHeight;
                for (String[] row : indicatorsData) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + cellMargin, yPosition);
                    contentStream.showText(row[0]);
                    contentStream.newLineAtOffset(250, 0);
                    contentStream.showText(row[1]);
                    contentStream.endText();
                    yPosition -= rowHeight;
                }

                // Sales Evolution Section
                yPosition -= 20;
                contentStream.beginText();
                contentStream.setFont(headerFont, 14);
                contentStream.newLineAtOffset(50, yPosition);
                contentStream.showText("Sales Evolution");
                contentStream.endText();

                // Sales Evolution Table Headers
                yPosition -= 20;
                contentStream.setFont(headerFont, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Month");
                contentStream.newLineAtOffset(100, 0);
                contentStream.showText("Completed Tasks");
                contentStream.newLineAtOffset(150, 0);
                contentStream.showText("Opportunity Value (TND)");
                contentStream.endText();

                // Draw line under headers
                contentStream.moveTo(margin, yPosition - 5);
                contentStream.lineTo(margin + tableWidth, yPosition - 5);
                contentStream.stroke();

                // Sales Evolution Data
                contentStream.setFont(textFont, 10);
                yPosition -= rowHeight;
                for (SalesEvolutionDTO evolution : report.getSalesEvolution()) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + cellMargin, yPosition);
                    contentStream.showText(evolution.getMonth());
                    contentStream.newLineAtOffset(100, 0);
                    contentStream.showText(String.valueOf(evolution.getCompletedTasks()));
                    contentStream.newLineAtOffset(150, 0);
                    contentStream.showText(evolution.getOpportunityValue().toString());
                    contentStream.endText();
                    yPosition -= rowHeight;
                }

                // Sales Performance Section
                yPosition -= 20;
                contentStream.beginText();
                contentStream.setFont(headerFont, 14);
                contentStream.newLineAtOffset(50, yPosition);
                contentStream.showText("Sales Performance");
                contentStream.endText();

                // Sales Performance Table Headers
                yPosition -= 20;
                contentStream.setFont(headerFont, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("User");
                contentStream.newLineAtOffset(100, 0);
                contentStream.showText("Target (TND)");
                contentStream.newLineAtOffset(100, 0);
                contentStream.showText("Achieved (TND)");
                contentStream.newLineAtOffset(100, 0);
                contentStream.showText("Progress (%)");
                contentStream.endText();

                // Draw line under headers
                contentStream.moveTo(margin, yPosition - 5);
                contentStream.lineTo(margin + tableWidth, yPosition - 5);
                contentStream.stroke();

                // Sales Performance Data
                contentStream.setFont(textFont, 10);
                yPosition -= rowHeight;
                for (SalesPerformanceDTO performance : report.getSalesPerformance()) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + cellMargin, yPosition);
                    contentStream.showText(performance.getName());
                    contentStream.newLineAtOffset(100, 0);
                    contentStream.showText(performance.getTarget().toString());
                    contentStream.newLineAtOffset(100, 0);
                    contentStream.showText(performance.getAchieved().toString());
                    contentStream.newLineAtOffset(100, 0);
                    contentStream.showText(String.format("%.2f%%", performance.getProgress()));
                    contentStream.endText();
                    yPosition -= rowHeight;
                }
            }

            document.save(out);
            return out.toByteArray();
        }
    }
    private void addPdfLine(PDPageContentStream contentStream, String text, float x, float y) throws IOException {
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SecurityException("No authenticated user found. Please log in.");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}