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
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String TIME_ZONE = "Etc/GMT-1"; // GMT+1

    private final TaskRepository taskRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private Date parseDate(String dateStr, boolean isStart) {
        ZoneId localZone = ZoneId.of(TIME_ZONE);
        if (dateStr == null || dateStr.trim().isEmpty()) {
            LocalDateTime defaultTime = isStart
                    ? LocalDateTime.now(localZone).minusYears(1)
                    : LocalDateTime.now(localZone).plusDays(1);
            return Date.from(defaultTime.atZone(localZone).toInstant());
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(dateStr);
            return Date.from(localDateTime.atZone(localZone).toInstant());
        } catch (Exception e) {
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
        if (newStatus == StatutTask.Done || newStatus == StatutTask.Cancelled) {
            task.setCompletedAt(new Date());
        } else {
            task.setCompletedAt(null);
        }
        return taskRepository.save(task);
    }

    @Transactional
    public Task archiveTask(Long id) {
        User currentUser = getCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        if (!hasAdminPrivileges(currentUser) && !task.getAssignedUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only archive your own tasks");
        }
        if (task.isArchived()) {
            throw new RuntimeException("Task is already archived");
        }
        task.setArchived(true);
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
            Long opportunityId,
            String priorityStr,
            boolean archived,
            int page,
            int size,
            String sort) {
        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Date startDate = parseDate(startDateStr, true);
        Date endDate = parseDate(endDateStr, false);
        String filteredStatus = (status != null && !status.trim().isEmpty()) ? status : "ANY";
        Priority priority = (priorityStr != null && !priorityStr.trim().isEmpty() && !"ANY".equalsIgnoreCase(priorityStr))
                ? Priority.valueOf(priorityStr.toUpperCase())
                : null;

        if (!hasAdminPrivileges(currentUser) && assignedUserId != null &&
                !currentUser.getId().equals(assignedUserId)) {
            throw new SecurityException("Regular users can only filter their own tasks");
        }

        if (archived) {
            if (priority != null) {
                return taskRepository.findByArchivedTrueAndPriority(priority, pageable);
            } else if (opportunityId != null) {
                return taskRepository.findByArchivedTrueAndOpportunityId(opportunityId, pageable);
            } else if (!"ANY".equals(filteredStatus)) {
                StatutTask statutTask = StatutTask.valueOf(filteredStatus);
                return taskRepository.findByArchivedTrueAndStatutTask(statutTask, pageable);
            } else {
                return taskRepository.findByArchivedTrue(pageable);
            }
        }

        if (priority != null) {
            if (opportunityId != null) {
                if ("ANY".equals(filteredStatus)) {
                    if (unassignedOnly) {
                        return taskRepository.findByPriorityAndOpportunityIdAndAssignedUserIsNullAndDeadlineBetween(
                                priority, opportunityId, startDate, endDate, pageable);
                    } else if (assignedUserId != null) {
                        return taskRepository.findByPriorityAndOpportunityIdAndAssignedUserIdAndDeadlineBetween(
                                priority, opportunityId, assignedUserId, startDate, endDate, pageable);
                    } else {
                        return hasAdminPrivileges(currentUser)
                                ? taskRepository.findByPriorityAndOpportunityIdAndDeadlineBetween(priority, opportunityId, startDate, endDate, pageable)
                                : taskRepository.findByPriorityAndOpportunityIdAndAssignedUserIdAndDeadlineBetween(
                                priority, opportunityId, currentUser.getId(), startDate, endDate, pageable);
                    }
                } else {
                    StatutTask statutTask = StatutTask.valueOf(filteredStatus);
                    if (unassignedOnly) {
                        return taskRepository.findByPriorityAndOpportunityIdAndStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
                                priority, opportunityId, statutTask, startDate, endDate, pageable);
                    } else if (assignedUserId != null) {
                        return taskRepository.findByPriorityAndOpportunityIdAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
                                priority, opportunityId, statutTask, assignedUserId, startDate, endDate, pageable);
                    } else {
                        return hasAdminPrivileges(currentUser)
                                ? taskRepository.findByPriorityAndOpportunityIdAndStatutTaskAndDeadlineBetween(
                                priority, opportunityId, statutTask, startDate, endDate, pageable)
                                : taskRepository.findByPriorityAndOpportunityIdAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
                                priority, opportunityId, statutTask, currentUser.getId(), startDate, endDate, pageable);
                    }
                }
            } else {
                if ("ANY".equals(filteredStatus)) {
                    if (unassignedOnly) {
                        return taskRepository.findByPriorityAndAssignedUserIsNullAndDeadlineBetween(
                                priority, startDate, endDate, pageable);
                    } else if (assignedUserId != null) {
                        return taskRepository.findByPriorityAndAssignedUserIdAndDeadlineBetween(
                                priority, assignedUserId, startDate, endDate, pageable);
                    } else {
                        return hasAdminPrivileges(currentUser)
                                ? taskRepository.findByPriorityAndDeadlineBetween(priority, startDate, endDate, pageable)
                                : taskRepository.findByPriorityAndAssignedUserIdAndDeadlineBetween(
                                priority, currentUser.getId(), startDate, endDate, pageable);
                    }
                } else {
                    StatutTask statutTask = StatutTask.valueOf(filteredStatus);
                    if (unassignedOnly) {
                        return taskRepository.findByPriorityAndStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
                                priority, statutTask, startDate, endDate, pageable);
                    } else if (assignedUserId != null) {
                        return taskRepository.findByPriorityAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
                                priority, statutTask, assignedUserId, startDate, endDate, pageable);
                    } else {
                        return hasAdminPrivileges(currentUser)
                                ? taskRepository.findByPriorityAndStatutTaskAndDeadlineBetween(
                                priority, statutTask, startDate, endDate, pageable)
                                : taskRepository.findByPriorityAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
                                priority, statutTask, currentUser.getId(), startDate, endDate, pageable);
                    }
                }
            }
        } else if (opportunityId != null) {
            if ("ANY".equals(filteredStatus)) {
                if (unassignedOnly) {
                    return taskRepository.findByOpportunityIdAndAssignedUserIsNullAndDeadlineBetween(
                            opportunityId, startDate, endDate, pageable);
                } else if (assignedUserId != null) {
                    return taskRepository.findByOpportunityIdAndAssignedUserIdAndDeadlineBetween(
                            opportunityId, assignedUserId, startDate, endDate, pageable);
                } else {
                    return hasAdminPrivileges(currentUser)
                            ? taskRepository.findByOpportunityIdAndDeadlineBetween(opportunityId, startDate, endDate, pageable)
                            : taskRepository.findByOpportunityIdAndAssignedUserIdAndDeadlineBetween(
                            opportunityId, currentUser.getId(), startDate, endDate, pageable);
                }
            } else {
                StatutTask statutTask = StatutTask.valueOf(filteredStatus);
                if (unassignedOnly) {
                    return taskRepository.findByOpportunityIdAndStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
                            opportunityId, statutTask, startDate, endDate, pageable);
                } else if (assignedUserId != null) {
                    return taskRepository.findByOpportunityIdAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
                            opportunityId, statutTask, assignedUserId, startDate, endDate, pageable);
                } else {
                    return hasAdminPrivileges(currentUser)
                            ? taskRepository.findByOpportunityIdAndStatutTaskAndDeadlineBetween(
                            opportunityId, statutTask, startDate, endDate, pageable)
                            : taskRepository.findByOpportunityIdAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
                            opportunityId, statutTask, currentUser.getId(), startDate, endDate, pageable);
                }
            }
        } else {
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
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SecurityException("No authenticated user found. Please log in.");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Current user not found in database: " + email));
    }

    private void createNotification(User assignedUser, Task task, Notification.NotificationType type) {
        String message;
        switch (type) {
            case TASK_ASSIGNED:
                message = "You have been assigned a new task: " + task.getTitle() + " for opportunity: " + task.getOpportunity().getTitle();
                break;
            case TASK_UPCOMING:
                message = "Reminder: Task '" + task.getTitle() + "' is due in 24 hours!";
                break;
            case TASK_OVERDUE:
                message = "Task '" + task.getTitle() + "' is overdue!";
                break;
            default:
                message = "Notification for task: " + task.getTitle();
        }
        String link = "/tasks/" + task.getId();
        Notification notification = Notification.builder()
                .message(message)
                .timestamp(LocalDateTime.now(ZoneId.of(TIME_ZONE)))
                .isRead(false)
                .user(assignedUser)
                .type(type)
                .link(link)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public Task createTask(Task task, Long opportunityId, Long assignedUserId) {
        User currentUser = getCurrentUser();
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + opportunityId));

        ZoneId localZone = ZoneId.of(TIME_ZONE);
        LocalDateTime localDeadline = LocalDateTime.ofInstant(task.getDeadline().toInstant(), localZone);
        LocalDateTime localDate = localDeadline;
        LocalDateTime today = LocalDateTime.now(localZone);
        if (localDate.isBefore(today.plusDays(1))) {
            throw new IllegalArgumentException("Deadline must be at least tomorrow");
        }

        User assignedUser = null;
        if (assignedUserId != null) {
            assignedUser = userRepository.findById(assignedUserId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + assignedUserId));
            if (!hasAdminPrivileges(currentUser) && !currentUser.getId().equals(assignedUserId)) {
                throw new SecurityException("Regular users can only assign tasks to themselves");
            }
        }

        task.setOpportunity(opportunity);
        task.setAssignedUser(assignedUser);
        if (task.getStatutTask() == null) {
            task.setStatutTask(StatutTask.ToDo);
        }
        task.setArchived(false);

        Task savedTask = taskRepository.save(task);
        if (assignedUser != null) {
            createNotification(assignedUser, savedTask, Notification.NotificationType.TASK_ASSIGNED);
        }
        return savedTask;
    }

    @Transactional
    public Task updateTask(Long id, Task taskDetails, Long assignedUserId) {
        User currentUser = getCurrentUser();
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        User previousAssignedUser = task.getAssignedUser();

        if (task.isArchived()) {
            throw new RuntimeException("Cannot update archived tasks");
        }

        // Apply restrictions for non-admin users when task is InProgress
        if (task.getStatutTask() == StatutTask.InProgress && !hasAdminPrivileges(currentUser)) {
            // Only allow updating priority and assigned user for regular users
            if (taskDetails.getTitle() != null || taskDetails.getDeadline() != null ||
                    taskDetails.getTypeTask() != null || taskDetails.getOpportunity() != null ||
                    taskDetails.getDescription() != null) {
                throw new IllegalArgumentException("In-progress tasks can only update priority and assigned user for regular users");
            }
        }

        if (taskDetails.getDeadline() != null) {
            ZoneId localZone = ZoneId.of(TIME_ZONE);
            LocalDateTime localDeadline = LocalDateTime.ofInstant(taskDetails.getDeadline().toInstant(), localZone);
            LocalDateTime localDate = localDeadline;
            LocalDateTime today = LocalDateTime.now(localZone);
            if (localDate.isBefore(today.plusDays(1))) {
                throw new IllegalArgumentException("Deadline must be at least tomorrow");
            }
        }

        if (assignedUserId != null) {
            User assignedUser = userRepository.findById(assignedUserId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + assignedUserId));
            if (!hasAdminPrivileges(currentUser) && !currentUser.getId().equals(assignedUserId)) {
                throw new SecurityException("Regular users can only assign tasks to themselves");
            }
            if (previousAssignedUser == null || !previousAssignedUser.getId().equals(assignedUserId)) {
                task.setAssignedUser(assignedUser);
                createNotification(assignedUser, task, Notification.NotificationType.TASK_ASSIGNED);
            }
        }

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

    @Scheduled(fixedRate = 60000) // Check every minute
    @Transactional
    public void checkOverdueTasks() {
        ZoneId localZone = ZoneId.of(TIME_ZONE);
        LocalDateTime nowLocal = LocalDateTime.now(localZone);
        Date nowUtc = Date.from(nowLocal.atZone(localZone).toInstant());
        List<Task> overdueTasks = taskRepository.findByStatutTaskAndDeadlineBefore(
                StatutTask.InProgress,
                nowUtc
        );
        for (Task task : overdueTasks) {
            User assignedUser = task.getAssignedUser();
            if (assignedUser != null) {
                String link = "/tasks/" + task.getId();
                boolean hasReminder = notificationRepository.existsByUserAndTypeAndLink(
                        assignedUser,
                        Notification.NotificationType.TASK_OVERDUE,
                        link
                );
                if (!hasReminder) {
                    createNotification(assignedUser, task, Notification.NotificationType.TASK_OVERDUE);
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    public void checkUpcomingDeadlines() {
        ZoneId localZone = ZoneId.of(TIME_ZONE);
        LocalDateTime nowLocal = LocalDateTime.now(localZone);
        LocalDateTime in24HoursLocal = nowLocal.plusHours(24);
        Date start = Date.from(nowLocal.atZone(localZone).toInstant());
        Date end = Date.from(in24HoursLocal.atZone(localZone).toInstant());

        List<Task> upcomingTasks = taskRepository.findByStatutTaskNotInAndDeadlineBetween(
                List.of(StatutTask.Done, StatutTask.Cancelled),
                start,
                end
        );
        for (Task task : upcomingTasks) {
            User assignedUser = task.getAssignedUser();
            if (assignedUser != null) {
                String link = "/tasks/" + task.getId();
                boolean hasReminder = notificationRepository.existsByUserAndTypeAndLink(
                        assignedUser,
                        Notification.NotificationType.TASK_UPCOMING,
                        link
                );
                if (!hasReminder) {
                    createNotification(assignedUser, task, Notification.NotificationType.TASK_UPCOMING);
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    public void archiveCompletedTasks() {
        ZoneId localZone = ZoneId.of(TIME_ZONE);
        LocalDateTime nowLocal = LocalDateTime.now(localZone);
        LocalDateTime twentyFourHoursAgo = nowLocal.minusHours(24);
        Date cutoff = Date.from(twentyFourHoursAgo.atZone(localZone).toInstant());

        List<Task> tasksToArchive = taskRepository.findByStatutTaskNotInAndDeadlineBetween(
                List.of(StatutTask.ToDo, StatutTask.InProgress),
                new Date(0), cutoff
        );

        for (Task task : tasksToArchive) {
            if (task.getOpportunity() != null) {
                String oppStatus = task.getOpportunity().getStatus().toString();
                if (oppStatus != null && (oppStatus.equalsIgnoreCase("WON") || oppStatus.equalsIgnoreCase("LOST"))) {
                    task.setArchived(true);
                    taskRepository.save(task);
                }
            }
        }
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
        List<Task> tasks = taskRepository.findByOpportunityId(id);
        if (!tasks.isEmpty()) {
            for (Task task : tasks) {
                task.setOpportunity(null);
                task.setStatutTask(StatutTask.Cancelled);
                task.setCompletedAt(new Date());
            }
            taskRepository.saveAll(tasks);
        }
    }
}