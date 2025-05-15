package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.Priority;
import crm.dopaflow_backend.Model.StatutTask;
import crm.dopaflow_backend.Model.Task;
import crm.dopaflow_backend.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByAssignedUser(User assignedUser, Pageable pageable);
    List<Task> findByOpportunityId(Long opportunityId);
    List<Task> findByAssignedUserId(Long userId);
    Page<Task> findByAssignedUserId(Long userId, Pageable pageable);

    Page<Task> findByTitleContainingIgnoreCase(String query, Pageable pageable);

    Page<Task> findByAssignedUserIsNullAndDeadlineBetween(
            Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByAssignedUserIdAndDeadlineBetween(
            Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByDeadlineBetween(
            Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
            StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByStatutTaskAndAssignedUserIdAndDeadlineBetween(
            StatutTask statutTask, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByStatutTaskAndDeadlineBetween(
            StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByOpportunityIdAndAssignedUserIsNullAndDeadlineBetween(
            Long opportunityId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByOpportunityIdAndAssignedUserIdAndDeadlineBetween(
            Long opportunityId, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByOpportunityIdAndDeadlineBetween(
            Long opportunityId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByOpportunityIdAndStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
            Long opportunityId, StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByOpportunityIdAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
            Long opportunityId, StatutTask statutTask, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByOpportunityIdAndStatutTaskAndDeadlineBetween(
            Long opportunityId, StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndAssignedUserIsNullAndDeadlineBetween(
            Priority priority, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndAssignedUserIdAndDeadlineBetween(
            Priority priority, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndDeadlineBetween(
            Priority priority, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
            Priority priority, StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
            Priority priority, StatutTask statutTask, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndStatutTaskAndDeadlineBetween(
            Priority priority, StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndOpportunityIdAndAssignedUserIsNullAndDeadlineBetween(
            Priority priority, Long opportunityId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndOpportunityIdAndAssignedUserIdAndDeadlineBetween(
            Priority priority, Long opportunityId, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndOpportunityIdAndDeadlineBetween(
            Priority priority, Long opportunityId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndOpportunityIdAndStatutTaskAndAssignedUserIsNullAndDeadlineBetween(
            Priority priority, Long opportunityId, StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndOpportunityIdAndStatutTaskAndAssignedUserIdAndDeadlineBetween(
            Priority priority, Long opportunityId, StatutTask statutTask, Long assignedUserId, Date startDate, Date endDate, Pageable pageable);

    Page<Task> findByPriorityAndOpportunityIdAndStatutTaskAndDeadlineBetween(
            Priority priority, Long opportunityId, StatutTask statutTask, Date startDate, Date endDate, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.statutTask = :status")
    long countCompletedTasks(@Param("status") StatutTask status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.statutTask IN (:toDo, :inProgress)")
    long countPendingTasks(@Param("toDo") StatutTask toDo, @Param("inProgress") StatutTask inProgress);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.statutTask = :status AND t.assignedUser.id = :userId")
    long countCompletedTasksForUser(@Param("status") StatutTask status, @Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.statutTask IN (:toDo, :inProgress) AND t.assignedUser.id = :userId")
    long countPendingTasksForUser(@Param("toDo") StatutTask toDo, @Param("inProgress") StatutTask inProgress, @Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.statutTask = :status AND t.deadline >= :startDate")
    long countCompletedTasksSince(@Param("status") StatutTask status, @Param("startDate") Date startDate);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.statutTask = :status AND t.deadline >= :startDate AND t.assignedUser.id = :userId")
    long countCompletedTasksSinceForUser(@Param("status") StatutTask status, @Param("startDate") Date startDate, @Param("userId") Long userId);

    @Query("SELECT t FROM Task t WHERE t.statutTask = :status AND t.deadline >= :startDate")
    List<Task> findCompletedTasksSince(@Param("status") StatutTask status, @Param("startDate") Date startDate);

    @Query("SELECT t FROM Task t WHERE t.statutTask = :status AND t.deadline >= :startDate AND t.assignedUser.id = :userId")
    List<Task> findCompletedTasksSinceForUser(@Param("status") StatutTask status, @Param("startDate") Date startDate, @Param("userId") Long userId);

    List<Task> findByStatutTaskAndDeadlineBefore(StatutTask statutTask, Date deadline);
    List<Task> findByStatutTaskNotInAndDeadlineBetween(List<StatutTask> statuses, Date start, Date end);

    // Updated methods for archived tasks
    Page<Task> findByArchivedTrue(Pageable pageable);

    Page<Task> findByArchivedTrueAndOpportunityId(Long opportunityId, Pageable pageable);

    Page<Task> findByArchivedTrueAndStatutTask(StatutTask statutTask, Pageable pageable);

    Page<Task> findByArchivedTrueAndPriority(Priority priority, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.archived = true AND t.completedAt >= :startDate AND t.completedAt <= :endDate")
    Page<Task> findByArchivedTrueAndCompletedAtBetween(@Param("startDate") Date startDate, @Param("endDate") Date endDate, Pageable pageable);
}