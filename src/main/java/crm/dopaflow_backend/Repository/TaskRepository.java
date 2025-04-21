package crm.dopaflow_backend.Repository;

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
}