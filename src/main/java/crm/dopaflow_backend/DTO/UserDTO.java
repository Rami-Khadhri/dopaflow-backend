package crm.dopaflow_backend.DTO;

import crm.dopaflow_backend.Model.User;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String profilePhotoUrl;

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.profilePhotoUrl = user.getProfilePhotoUrl();
    }
}