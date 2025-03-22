package crm.dopaflow_backend.DTO;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String profilePhotoUrl;
    private String email;
}