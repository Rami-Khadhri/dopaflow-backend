package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // For login/email verification
    Optional<User> findByEmail(String email);

    // For username uniqueness check
    Optional<User> findByUsername(String username);

    // For email verification token
    Optional<User> findByVerificationToken(String verificationToken);

}