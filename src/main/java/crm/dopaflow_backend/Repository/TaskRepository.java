package crm.dopaflow_backend.Repository;


import crm.dopaflow_backend.Model.StatutTask;
import crm.dopaflow_backend.Model.Task;
import crm.dopaflow_backend.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByAssignedUser(User assignedUser, Pageable pageable);
    List<Task> findByOpportunityId(Long opportunityId);
    List<Task> findByAssignedUserId(Long userId);
    Page<Task> findByAssignedUserId(Long userId, Pageable pageable);

    // Changed from findByNameContainingIgnoreCase to findByTitleContainingIgnoreCase
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

}