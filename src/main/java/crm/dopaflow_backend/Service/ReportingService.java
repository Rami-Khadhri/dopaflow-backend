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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(ReportingService.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM");

    private final OpportunityRepository opportunityRepository;
    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ReportDTO generateReport() {
        logger.info("Generating report...");
        long startTime = System.currentTimeMillis();
        ReportDTO report = new ReportDTO();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sixMonthsAgo = now.minus(6, ChronoUnit.MONTHS);
        LocalDateTime oneMonthAgo = now.minus(1, ChronoUnit.MONTHS);
        Date sixMonthsAgoDate = Date.from(sixMonthsAgo.atZone(ZoneId.systemDefault()).toInstant());

        // Get current user
        User currentUser = getCurrentUser();
        logger.info("Current user: {}", currentUser.getEmail());

        // --- KPIs ---
        KeyIndicatorsDTO keyIndicators = new KeyIndicatorsDTO();

        // 1. Total value of WON opportunities
        Double totalOpportunityValue = opportunityRepository.getTotalOpportunityValue(StatutOpportunity.WON);
        keyIndicators.setTotalOpportunityValue(totalOpportunityValue != null ? BigDecimal.valueOf(totalOpportunityValue) : BigDecimal.ZERO);
        logger.info("Total Opportunity Value: {}", keyIndicators.getTotalOpportunityValue());

        // 2. Count of new opportunities in the last month
        List<Opportunity> recentOpportunities = opportunityRepository.findWonOpportunitiesSince(StatutOpportunity.IN_PROGRESS, oneMonthAgo);
        long newOpportunities = recentOpportunities.size();
        keyIndicators.setNewOpportunities(newOpportunities);
        logger.info("New Opportunities (last month): {}", newOpportunities);

        // 3. Total completed tasks
        long completedTasks = taskRepository.countCompletedTasks(StatutTask.Done);
        keyIndicators.setCompletedTasks(completedTasks);
        logger.info("Completed Tasks: {}", completedTasks);

        long totalOpportunitiesForUser = opportunityRepository.countOpportunitiesForUser(currentUser.getId());
        keyIndicators.setTotalOpportunitiesForUser(totalOpportunitiesForUser);
        logger.info("total opportunities for" + currentUser.getUsername() + "= " + totalOpportunitiesForUser);

        // 5. New companies in the last month
        long newCompanies = companyRepository.countNewCompaniesSince(oneMonthAgo);
        keyIndicators.setNewCompanies(newCompanies);
        logger.info("New Companies: {}", newCompanies);

        // 6. New contacts in the last month
        long newContacts = contactRepository.countNewContactsSince(oneMonthAgo);
        keyIndicators.setNewContacts(newContacts);
        logger.info("New Contacts: {}", newContacts);

        report.setKeyIndicators(keyIndicators);

        // --- Evolution Chart (Last 6 Months) ---
        List<Task> completedTasksList = taskRepository.findCompletedTasksSince(StatutTask.Done, sixMonthsAgoDate);
        List<Opportunity> wonOpportunities = opportunityRepository.findWonOpportunitiesSince(StatutOpportunity.WON, sixMonthsAgo);
        logger.info("Fetched {} completed tasks and {} won opportunities", completedTasksList.size(), wonOpportunities.size());

        List<SalesEvolutionDTO> salesEvolutionData = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minus(i, ChronoUnit.MONTHS).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            SalesEvolutionDTO monthData = new SalesEvolutionDTO();
            monthData.setMonth(monthStart.format(MONTH_FORMATTER).toUpperCase());
            monthData.setCompletedTasks(0L);
            monthData.setOpportunityValue(BigDecimal.ZERO);
            salesEvolutionData.add(monthData);
        }

        // Populate completed tasks using deadline
        for (Task task : completedTasksList) {
            if (task.getDeadline() == null) continue;
            LocalDateTime deadline = LocalDateTime.ofInstant(task.getDeadline().toInstant(), ZoneId.systemDefault());
            if (deadline.isBefore(sixMonthsAgo)) continue;
            String monthKey = deadline.format(MONTH_FORMATTER).toUpperCase();
            salesEvolutionData.stream()
                    .filter(data -> data.getMonth().equals(monthKey))
                    .findFirst()
                    .ifPresent(data -> data.setCompletedTasks(data.getCompletedTasks() + 1));
        }

        // Populate WON opportunities value
        for (Opportunity op : wonOpportunities) {
            LocalDateTime createdDate = op.getCreatedAt();
            if (createdDate == null || createdDate.isBefore(sixMonthsAgo)) continue;
            String monthKey = createdDate.format(MONTH_FORMATTER).toUpperCase();
            salesEvolutionData.stream()
                    .filter(data -> data.getMonth().equals(monthKey))
                    .findFirst()
                    .ifPresent(data -> {
                        BigDecimal value = op.getValue() != null ? op.getValue() : BigDecimal.ZERO;
                        data.setOpportunityValue(data.getOpportunityValue().add(value));
                    });
        }
        report.setSalesEvolution(salesEvolutionData);
        logger.info("Sales Evolution Data: {}", salesEvolutionData);

        // --- Performance Table ---
        List<SalesPerformanceDTO> salesPerformance = new ArrayList<>();
        List<User> users = userRepository.findAll();
        for (User user : users) {
            List<Opportunity> userWonOpportunities = opportunityRepository.findWonOpportunitiesSinceForUser(StatutOpportunity.WON, sixMonthsAgo, user.getId());
            BigDecimal achieved = userWonOpportunities.stream()
                    .map(op -> op.getValue() != null ? op.getValue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = new BigDecimal("3000.0");

            SalesPerformanceDTO userPerformance = new SalesPerformanceDTO();
            userPerformance.setName(user.getUsername() != null ? user.getUsername() : user.getEmail());
            userPerformance.setTarget(target);
            userPerformance.setAchieved(achieved);
            double progress = target.compareTo(BigDecimal.ZERO) != 0
                    ? achieved.divide(target, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;
            userPerformance.setProgress(progress);
            salesPerformance.add(userPerformance);
        }
        report.setSalesPerformance(salesPerformance);
        logger.info("Sales Performance Data: {}", salesPerformance);

        logger.info("Report generated in {} ms", System.currentTimeMillis() - startTime);
        return report;
    }

    public byte[] generateExcelReport(ReportDTO report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create a sheet
            Sheet sheet = workbook.createSheet("CRM Report");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create Key Indicators Section
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("Key Indicators");
            headerCell.setCellStyle(headerStyle);

            KeyIndicatorsDTO indicators = report.getKeyIndicators();
            createRow(sheet, rowNum++, "Total Opportunity Value (TND)", indicators.getTotalOpportunityValue().toString());
            createRow(sheet, rowNum++, "New Opportunities", String.valueOf(indicators.getNewOpportunities()));
            createRow(sheet, rowNum++, "Completed Tasks", String.valueOf(indicators.getCompletedTasks()));
            createRow(sheet, rowNum++, "Total Opportunities for User", String.valueOf(indicators.getTotalOpportunitiesForUser()));
            createRow(sheet, rowNum++, "New Companies", String.valueOf(indicators.getNewCompanies()));
            createRow(sheet, rowNum++, "New Contacts", String.valueOf(indicators.getNewContacts()));

            // Add empty row
            rowNum++;

            // Create Sales Evolution Section
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

            // Add empty row
            rowNum++;

            // Create Sales Performance Section
            headerRow = sheet.createRow(rowNum++);
            headerCell = headerRow.createCell(0);
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

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
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
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("CRM Report");
                contentStream.endText();

                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 720);
                contentStream.showText("Key Indicators");
                contentStream.endText();

                contentStream.setFont(PDType1Font.HELVETICA, 10);
                KeyIndicatorsDTO indicators = report.getKeyIndicators();
                int yPosition = 700;
                addPdfLine(contentStream, "Total Opportunity Value: " + indicators.getTotalOpportunityValue() + " TND", 50, yPosition);
                yPosition -= 20;
                addPdfLine(contentStream, "New Opportunities: " + indicators.getNewOpportunities(), 50, yPosition);
                yPosition -= 20;
                addPdfLine(contentStream, "Completed Tasks: " + indicators.getCompletedTasks(), 50, yPosition);
                yPosition -= 20;
                addPdfLine(contentStream, "Total Opportunities for User: " + indicators.getTotalOpportunitiesForUser(), 50, yPosition);
                yPosition -= 20;
                addPdfLine(contentStream, "New Companies: " + indicators.getNewCompanies(), 50, yPosition);
                yPosition -= 20;
                addPdfLine(contentStream, "New Contacts: " + indicators.getNewContacts(), 50, yPosition);

                yPosition -= 30;
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, yPosition);
                contentStream.showText("Sales Evolution");
                contentStream.endText();

                yPosition -= 20;
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                for (SalesEvolutionDTO evolution : report.getSalesEvolution()) {
                    addPdfLine(contentStream, String.format("%s: %d tasks, %s TND", evolution.getMonth(), evolution.getCompletedTasks(), evolution.getOpportunityValue()), 50, yPosition);
                    yPosition -= 15;
                }

                yPosition -= 30;
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, yPosition);
                contentStream.showText("Sales Performance");
                contentStream.endText();

                yPosition -= 20;
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                for (SalesPerformanceDTO performance : report.getSalesPerformance()) {
                    addPdfLine(contentStream, String.format("%s: Target %s TND, Achieved %s TND, Progress %.2f%%",
                            performance.getName(), performance.getTarget(), performance.getAchieved(), performance.getProgress()), 50, yPosition);
                    yPosition -= 15;
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
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
            logger.error("No authenticated user found");
            throw new SecurityException("No authenticated user found. Please log in.");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}