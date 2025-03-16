package crm.dopaflow_backend.DTO;

import crm.dopaflow_backend.Model.Task;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class TaskDTO {
    private Long id;
    private String title;
    private String description;
    private Date deadline;
    private String priority;
    private String statutTask;
    private String typeTask;
    private Long opportunityId;
    private String opportunityTitle;
    private Long assignedUserId;
    private String assignedUserUsername;

    public TaskDTO(Task task) {
        this.id = task.getId();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.deadline = task.getDeadline();
        this.priority = task.getPriority() != null ? task.getPriority().name() : null;
        this.statutTask = task.getStatutTask() != null ? task.getStatutTask().name() : null;
        this.typeTask = task.getTypeTask() != null ? task.getTypeTask().name() : null;
        this.opportunityId = task.getOpportunity() != null ? task.getOpportunity().getId() : null;
        this.opportunityTitle = task.getOpportunity() != null ? task.getOpportunity().getTitle() : null;
        this.assignedUserId = task.getAssignedUser() != null ? task.getAssignedUser().getId() : null;
        this.assignedUserUsername = task.getAssignedUser() != null ? task.getAssignedUser().getUsername() : null;
    }
}