package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // For login/email verification
    Optional<User> findByEmail(String email);
    User findUserByEmail(String email);

    // For username uniqueness check
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);
    // For email verification token
    Optional<User> findByVerificationToken(String verificationToken);

}