package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.DTO.TaskDTO;
import crm.dopaflow_backend.Model.*;
import crm.dopaflow_backend.Repository.NotificationRepository;
import crm.dopaflow_backend.Repository.OpportunityRepository;
import crm.dopaflow_backend.Repository.TaskRepository;
import crm.dopaflow_backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private Date parseDate(String dateStr, boolean isStart) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return isStart
                    ? Date.from(LocalDateTime.now().minusYears(1).atZone(ZoneId.systemDefault()).toInstant())
                    : Date.from(LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault()).toInstant());
        }
        try {
            return Date.from(LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            System.out.println("Date parse error: " + dateStr);
            throw new RuntimeException("Invalid date format. Expected: yyyy-MM-dd'T'HH:mm:ss", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        User currentUser = getCurrentUser();
        return hasAdminPrivileges(currentUser)
                ? taskRepository.findAll()
                : taskRepository.findByAssignedUserId(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Task> getTaskById(Long id) {
        User currentUser = getCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        if (!hasAdminPrivileges(currentUser) && !task.getAssignedUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view your own tasks");
        }
        return Optional.of(task);
    }

    @Transactional(readOnly = true)
    public List<Task> getTaskByOpportunityId(Long opportunityId) {
        return taskRepository.findByOpportunityId(opportunityId);
    }

    @Transactional
    public void deleteTask(Long id) {
        User currentUser = getCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        if (!hasAdminPrivileges(currentUser) && !task.getAssignedUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only delete your own tasks");
        }
        taskRepository.delete(task);
    }

    @Transactional
    public Task updateTaskStatus(Long id, StatutTask newStatus) {
        User currentUser = getCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        if (!hasAdminPrivileges(currentUser) && !task.getAssignedUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only update the status of your own tasks");
        }
        task.setStatutTask(newStatus);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Page<Task> searchTasks(String query, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        return taskRepository.findByTitleContainingIgnoreCase(query, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> filterTasks(
            String status,
            String startDateStr,
            String endDateStr,
            Long assignedUserId,
            boolean unassignedOnly,
            int page,
            int size,
            String sort) {
        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Date startDate = parseDate(startDateStr, true);
        Date endDate = parseDate(endDateStr, false);
        String filteredStatus = (status != null && !status.trim().isEmpty()) ? status : "ANY";

        if (!hasAdminPrivileges(currentUser) && assignedUserId != null &&
                !currentUser.getId().equals(assignedUserId)) {
            throw new SecurityException("Regular users can only filter their own tasks");
        }

        if ("ANY".equals(filteredStatus)) {
            if (unassignedOnly) {
                return taskRepository.findByAssignedUserIsNullAndDeadlineBetween(startDate, endDate, pageable);
            } else if (assignedUserId != null) {
                return taskRepository.findByAssignedUserIdAndDeadlineBetween(assignedUserId, startDate, endDate, pageable);
            } else {
                return hasAdminPrivileges(currentUser)
                        ? taskRepository.findByDeadlineBetween(startDate, endDate, pageable)
                        : taskRepository.findByAssignedUserIdAndDeadlineBetween(currentUser.getId(), startDate, endDate, pageable);
            }
        } else {
            StatutTask statutTask = StatutTask.valueOf(filteredStatus);
            if (unassignedOnly) {
                return taskRepository.findByStatutTaskAndAssignedUserIsNullAndDeadlineBetween(statutTask, startDate, endDate, pageable);
            } else if (assignedUserId != null) {
                return taskRepository.findByStatutTaskAndAssignedUserIdAndDeadlineBetween(statutTask, assignedUserId, startDate, endDate, pageable);
            } else {
                return hasAdminPrivileges(currentUser)
                        ? taskRepository.findByStatutTaskAndDeadlineBetween(statutTask, startDate, endDate, pageable)
                        : taskRepository.findByStatutTaskAndAssignedUserIdAndDeadlineBetween(statutTask, currentUser.getId(), startDate, endDate, pageable);
            }
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SecurityException("No authenticated user found. Please log in.");
        }
        String email = authentication.getName(); // JWT sets this as the email (sub claim)
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Current user not found in database: " + email));
    }

    private void createNotification(User assignedUser, Task task) {
        String message = "You have been assigned a new task: " + task.getTitle() + " for opportunity: " + task.getOpportunity().getTitle();
        String link = "/tasks/" + task.getId();
        Notification notification = Notification.builder()
                .message(message)
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .user(assignedUser)
                .type(Notification.NotificationType.TASK_ASSIGNED)
                .link(link)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public Task createTask(Task task, Long opportunityId, Long assignedUserId) {
        User currentUser = getCurrentUser();
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + opportunityId));

        // Authorization: Admins can assign to anyone, regular users only to themselves
        User assignedUser = null;
        if (assignedUserId != null) {
            assignedUser = userRepository.findById(assignedUserId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + assignedUserId));
            if (!hasAdminPrivileges(currentUser) && !currentUser.getId().equals(assignedUserId)) {
                throw new SecurityException("Regular users can only assign tasks to themselves");
            }
        }

        task.setOpportunity(opportunity);
        task.setAssignedUser(assignedUser); // Can be null if unassigned
        if (task.getStatutTask() == null) {
            task.setStatutTask(StatutTask.ToDo);
        }

        Task savedTask = taskRepository.save(task);
        if (assignedUser != null) {
            createNotification(assignedUser, savedTask);
        }
        return savedTask;
    }

    @Transactional
    public Task updateTask(Long id, Task taskDetails, Long assignedUserId) {
        User currentUser = getCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        User previousAssignedUser = task.getAssignedUser();

        // Authorization for updating assigned user
        if (assignedUserId != null) {
            User assignedUser = userRepository.findById(assignedUserId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + assignedUserId));
            if (!hasAdminPrivileges(currentUser) && !currentUser.getId().equals(assignedUserId)) {
                throw new SecurityException("Regular users can only assign tasks to themselves");
            }
            if (previousAssignedUser == null || !previousAssignedUser.getId().equals(assignedUserId)) {
                task.setAssignedUser(assignedUser);
                createNotification(assignedUser, task);
            }
        }

        // Update fields only if provided in taskDetails
        if (taskDetails.getTitle() != null) task.setTitle(taskDetails.getTitle());
        if (taskDetails.getDescription() != null) task.setDescription(taskDetails.getDescription());
        if (taskDetails.getDeadline() != null) task.setDeadline(taskDetails.getDeadline());
        if (taskDetails.getPriority() != null) task.setPriority(taskDetails.getPriority());
        if (taskDetails.getStatutTask() != null) task.setStatutTask(taskDetails.getStatutTask());
        if (taskDetails.getTypeTask() != null) task.setTypeTask(taskDetails.getTypeTask());

        if (taskDetails.getOpportunity() != null && taskDetails.getOpportunity().getId() != null) {
            Opportunity opportunity = opportunityRepository.findById(taskDetails.getOpportunity().getId())
                    .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + taskDetails.getOpportunity().getId()));
            task.setOpportunity(opportunity);
        }

        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskDTO> getAllTasks(int page, int size, String sort) {
        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Page<Task> tasks;
        if (hasAdminPrivileges(currentUser)) {
            tasks = taskRepository.findAll(pageable);
        } else {
            tasks = taskRepository.findByAssignedUser(currentUser, pageable);
        }
        return tasks.map(TaskDTO::new);
    }

    private boolean hasAdminPrivileges(User user) {
        return user.getRole() == Role.Admin || user.getRole() == Role.SuperAdmin;
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "deadline");
        }
        String[] parts = sort.split(",");
        return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
    }

    @Transactional
    public void unassignTasksFromUser(Long userId) {
        List<Task> tasks = taskRepository.findByAssignedUserId(userId);
        if (!tasks.isEmpty()) {
            for (Task task : tasks) {
                task.setAssignedUser(null);
            }
            taskRepository.saveAll(tasks);
        }
    }
    @Transactional
    public void unassignTasksFromOpportunity(Long id) {
        // Find all tasks associated with the opportunity
        List<Task> tasks = taskRepository.findByOpportunityId(id);
        if (!tasks.isEmpty()) {
            for (Task task : tasks) {
                task.setOpportunity(null);
                task.setStatutTask(StatutTask.Cancelled);
            }
            taskRepository.saveAll(tasks);
        }

    }
}