package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.DTO.*;
import crm.dopaflow_backend.Model.*;
import crm.dopaflow_backend.Service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final OpportunityService opportunityService;
    private final ReportingService reportingService;
    private final SupportTicketService supportTicketService;
    private final TaskService taskService;
    private final UserService userService;
    private final ContactService contactService;
    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'SuperAdmin')")
    public ResponseEntity<DashboardDTO> getDashboard() {
        // Get current user to verify role explicitly
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        if (!currentUser.getRole().equals(Role.Admin) && !currentUser.getRole().equals(Role.SuperAdmin)) {
            throw new SecurityException("Access denied: Only Admin or SuperAdmin can view the dashboard");
        }

        // Key Indicators
        KeyIndicatorsDTO keyIndicators = getKeyIndicators();

        // Monthly Performance
        List<SalesEvolutionDTO> monthlyPerformance = getMonthlyPerformance();

        // Recent Activities
        List<ActivityDTO> recentActivities = getRecentActivities();

        // Active Users
        List<UserDTO> activeUsers = getActiveUsers();

        DashboardDTO dashboardDTO = new DashboardDTO(keyIndicators, monthlyPerformance, recentActivities, activeUsers);
        return ResponseEntity.ok(dashboardDTO);
    }

    private KeyIndicatorsDTO getKeyIndicators() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);

        long totalOpportunities = opportunityService.getAllOpportunities(0, Integer.MAX_VALUE, "createdAt,desc")
                .getTotalElements();
        BigDecimal wonOpportunitiesValue = reportingService.generateReport().getKeyIndicators().getTotalOpportunityValue();
        long activeTasks = taskService.getAllTasks(0, Integer.MAX_VALUE, "deadline,desc")
                .getContent().stream().filter(t -> t.getStatutTask()!= StatutTask.Done.toString()).count();
        long openSupportTickets = supportTicketService.getAllTicketSummaries().stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED && t.getStatus() != TicketStatus.RESOLVED).count();
        long newContacts = contactService.getAllContacts(0, Integer.MAX_VALUE, "createdAt,desc")
                .getContent().stream().filter(c -> c.getCreatedAt().isAfter(oneMonthAgo)).count();
        long newCompanies = companyService.getAllCompanies(0, Integer.MAX_VALUE, "name,asc")
                .getContent().stream().filter(c -> c.getCreatedAt().isAfter(oneMonthAgo)).count();

        return new KeyIndicatorsDTO(totalOpportunities, wonOpportunitiesValue, activeTasks, openSupportTickets, newContacts, newCompanies);
    }

    private List<SalesEvolutionDTO> getMonthlyPerformance() {
        ReportDTO report = reportingService.generateReport();
        return report.getSalesEvolution();
    }

    private List<ActivityDTO> getRecentActivities() {
        List<ActivityDTO> activities = new ArrayList<>();

        // Recent Opportunities
        opportunityService.getAllOpportunities(0, 5, "createdAt,desc").getContent()
                .forEach(o -> activities.add(new ActivityDTO("New opportunity: " + o.getTitle(), o.getCreatedAt())));

        // Recent Tasks
        taskService.getAllTasks(0, 5, "deadline,desc").getContent()
                .forEach(t -> activities.add(new ActivityDTO("Task updated: " + t.getTitle(), t.getDeadline() != null
                        ? LocalDateTime.ofInstant(t.getDeadline().toInstant(), ZoneId.systemDefault()) : LocalDateTime.now())));

        // Recent Tickets
        supportTicketService.getAllTicketSummaries().stream()
                .sorted(Comparator.comparing(TicketDTO::getCreatedAt).reversed())
                .limit(5)
                .forEach(t -> activities.add(new ActivityDTO("New ticket: " + t.getSubject(), t.getCreatedAt())));

        // Sort and limit to 5
        return activities.stream()
                .sorted(Comparator.comparing(ActivityDTO::date).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<UserDTO> getActiveUsers() {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        return userService.getAllUsersWithActivity().stream()
                .filter(u -> {
                    Boolean isOnline = (Boolean) u.get("isOnline");
                    Long lastActiveMillis = (Long) u.get("lastActive");
                    return (isOnline != null && isOnline) ||
                            (lastActiveMillis != null && LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(lastActiveMillis), ZoneId.systemDefault()).isAfter(oneDayAgo));
                })
                .map(u -> new UserDTO(
                        (String) u.get("username"),
                        (String) u.get("profilePhotoUrl"),
                        (String) u.get("role"),
                        u.get("lastActive") != null ? LocalDateTime.ofInstant(
                                Instant.ofEpochMilli((Long) u.get("lastActive")), ZoneId.systemDefault()) : null,
                        (String) u.get("status")))
                .limit(5)
                .collect(Collectors.toList());
    }
}

// DTOs
record DashboardDTO(KeyIndicatorsDTO keyIndicators, List<SalesEvolutionDTO> monthlyPerformance,
                    List<ActivityDTO> recentActivities, List<UserDTO> activeUsers) {}

record KeyIndicatorsDTO(long totalOpportunities, BigDecimal wonOpportunitiesValue, long activeTasks,
                        long openSupportTickets, long newContacts, long newCompanies) {}

record UserDTO(String name,String profilePhotoUrl, String role, LocalDateTime lastActivity, String status) {}