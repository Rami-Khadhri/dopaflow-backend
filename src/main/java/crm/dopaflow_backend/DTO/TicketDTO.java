package crm.dopaflow_backend.DTO;

import crm.dopaflow_backend.Model.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TicketDTO {
    private Long id;
    private String subject;
    private String content;
    private UserDTO creator;
    private UserDTO assignee;
    private TicketStatus status;
    private LocalDateTime createdAt;
    private List<TicketMessageDTO> messages;
    private long unreadCount; // Added to track unread messages
}