package crm.dopaflow_backend.DTO;

import lombok.Data;

@Data
public class MessageDTO {
    private Long ticketId;
    private String content;
    private Long senderId;
}