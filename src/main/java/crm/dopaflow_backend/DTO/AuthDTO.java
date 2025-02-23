package crm.dopaflow_backend.DTO;

import crm.dopaflow_backend.Model.Role;
import lombok.Data;

import java.util.Date;

@Data
public class AuthDTO {
    @Data
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
        private Role role;
        private Date birthdate;
    }

    @Data
    public static class LoginRequest {

        private String email;
        private String password;
    }
}