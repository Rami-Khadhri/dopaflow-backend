package crm.dopaflow_backend.DTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TicketMessageDTO {
    private Long id;
    private String content;
    private UserDTO sender;
    private LocalDateTime timestamp;
    private List<String> attachments;
    private boolean read; // Added to reflect message read status
}