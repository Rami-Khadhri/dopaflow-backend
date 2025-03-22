package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // For login/email verification

    Optional<User> findByEmail(String email);
    User findUserByEmail(String email);
    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<User> findByEmailOrUsernameContainingIgnoreCase(@Param("searchTerm") String searchTerm);
    List<User> findByEmailContainingIgnoreCase(String searchTerm);
    // For username uniqueness check
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);
    // For email verification token
    Optional<User> findByVerificationToken(String verificationToken);
    List<User> findByUsernameIn(List<String> usernames);

}