package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.*;
import crm.dopaflow_backend.Repository.UserRepository;
import crm.dopaflow_backend.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public User registerUser(String username, String email, String password, Role role, Date birthdate) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .birthdate(birthdate)
                .status(StatutUser.Active)
                .lastLogin(new Date())
                .verificationToken(UUID.randomUUID().toString())
                .verified(false)
                .build();
        userRepository.save(user);
        emailService.sendVerificationEmail(email, user.getVerificationToken());
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));
        user.setVerified(true);
        return userRepository.save(user);
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }

    public User getUserFromToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid token format");
        }
        String jwt = token.substring(7);
        String email = jwtUtil.extractEmail(jwt);
        return findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }
    public Optional<User> findByVerificationToken(String token) {
        return userRepository.findByVerificationToken(token);
    }
    public User updateUser(String email, String username, String currentPassword, String newPassword, Boolean twoFactorEnabled) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (username != null && !username.equals(user.getUsername())) {
            if (userRepository.findByUsername(username).isPresent()) {
                throw new IllegalArgumentException("Username already taken");
            }
            user.setUsername(username);
        }

        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        if (twoFactorEnabled != null) {
            if (!twoFactorEnabled && user.isTwoFactorEnabled()) {
                user.setTwoFactorEnabled(false);
                user.setTwoFactorSecret(null);
            } else if (twoFactorEnabled && !user.isTwoFactorEnabled()) {
                throw new IllegalArgumentException("Use 2FA enable endpoint to activate 2FA");
            }
        }

        return userRepository.save(user);
    }


    public User createUser(String username, String email, String password, Role role, Date birthdate) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already taken");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .birthdate(birthdate)
                .status(StatutUser.Active)
                .lastLogin(new Date())
                .verificationToken(UUID.randomUUID().toString())
                .verified(false)
                .twoFactorEnabled(false)
                .build();

        userRepository.save(user);
        emailService.sendVerificationEmail(email, user.getVerificationToken());
        return user;
    }

    // READ
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }


    // UPDATE
    public User updateUser(Long id, User updatedUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getEmail().equals(updatedUser.getEmail()) &&
                userRepository.findByEmail(updatedUser.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use");
        }

        user.setUsername(updatedUser.getUsername());
        user.setEmail(updatedUser.getEmail());
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }
        user.setRole(updatedUser.getRole());
        user.setBirthdate(updatedUser.getBirthdate());
        user.setStatus(updatedUser.getStatus());

        return userRepository.save(user);
    }

    // DELETE
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.delete(user);
    }
}