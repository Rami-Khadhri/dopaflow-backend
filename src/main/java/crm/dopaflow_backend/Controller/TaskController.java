package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.StatutTask;
import crm.dopaflow_backend.Model.Task;
import crm.dopaflow_backend.DTO.TaskDTO;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @GetMapping("/all")
    public ResponseEntity<Page<TaskDTO>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "deadline,desc") String sort) {
        try {
            return ResponseEntity.ok(taskService.getAllTasks(page, size, sort));
        } catch (RuntimeException e) {
            System.out.println("Error fetching tasks: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<TaskDTO> getTask(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(taskService.getTaskById(id)
                    .map(TaskDTO::new)
                    .orElseThrow(() -> new RuntimeException("Task not found with id: " + id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PostMapping("/add")
    public ResponseEntity<?> createTask(
            @RequestBody Task task,
            @RequestParam Long opportunityId,
            @RequestParam Long assignedUserId,
            @AuthenticationPrincipal User currentUser,
            BindingResult bindingResult) {
        System.out.println("Received: Task=" + task + ", opportunityId=" + opportunityId + ", assignedUserId=" + assignedUserId + ", currentUser=" + (currentUser != null ? currentUser.getUsername() : "null"));

        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            System.out.println("Validation errors: " + errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Validation failed: " + errors);
        }

        try {
            Task createdTask = taskService.createTask(task, opportunityId, assignedUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(new TaskDTO(createdTask));
        } catch (RuntimeException e) {
            System.out.println("Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateTask(
            @PathVariable Long id,
            @RequestBody Task taskDetails,
            @RequestParam(required = false) Long assignedUserId,
            BindingResult bindingResult) {
        System.out.println("Received updateTask: id=" + id + ", taskDetails=" + taskDetails + ", assignedUserId=" + assignedUserId);
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            System.out.println("Validation errors: " + errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Validation failed: " + errors);
        }
        try {
            Task updatedTask = taskService.updateTask(id, taskDetails, assignedUserId);
            return ResponseEntity.ok(new TaskDTO(updatedTask));
        } catch (RuntimeException e) {
            System.out.println("Error updating task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        try {
            taskService.deleteTask(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/change-status/{id}")
    public ResponseEntity<TaskDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            StatutTask newStatus = StatutTask.valueOf(status);
            Task updatedTask = taskService.updateTaskStatus(id, newStatus);
            return ResponseEntity.ok(new TaskDTO(updatedTask));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<TaskDTO>> filterTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long assignedUserId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "deadline,desc") String sort) {
        return ResponseEntity.ok(taskService.filterTasks(status, startDate, endDate, assignedUserId, unassignedOnly, page, size, sort)
                .map(TaskDTO::new));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TaskDTO>> searchTasks(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "title,asc") String sort) {
        return ResponseEntity.ok(taskService.searchTasks(query, page, size, sort).map(TaskDTO::new));
    }

    @GetMapping("/opportunity/{opportunityId}")
    public ResponseEntity<List<TaskDTO>> getTasksByOpportunityId(@PathVariable Long opportunityId) {
        return ResponseEntity.ok(taskService.getTaskByOpportunityId(opportunityId).stream()
                .map(TaskDTO::new)
                .collect(Collectors.toList()));
    }
}