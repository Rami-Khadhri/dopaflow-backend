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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // Workaround: Fetch opportunities since oneMonthAgo and filter by IN_PROGRESS
        List<Opportunity> recentOpportunities = opportunityRepository.findWonOpportunitiesSince(StatutOpportunity.IN_PROGRESS, oneMonthAgo);
        long newOpportunities = recentOpportunities.size();
        keyIndicators.setNewOpportunities(newOpportunities);
        logger.info("New Opportunities (last month): {}", newOpportunities);

        // 3. Total completed tasks
        long completedTasks = taskRepository.countCompletedTasks(StatutTask.Done);
        keyIndicators.setCompletedTasks(completedTasks);
        logger.info("Completed Tasks: {}", completedTasks);

        // 4. Customer satisfaction (mocked)
        keyIndicators.setCustomerSatisfaction(92.0);
        logger.info("Customer Satisfaction: 92.0% (mocked)");

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
            BigDecimal target = new BigDecimal("15000.0");

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