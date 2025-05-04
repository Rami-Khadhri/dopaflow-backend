package crm.dopaflow_backend.DTO;

import java.time.LocalDateTime;

public record ActivityDTO(
        String description,
        LocalDateTime date
) {}