package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.*;
import crm.dopaflow_backend.Repository.UserRepository;
import crm.dopaflow_backend.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ContactService contactService;
    private final JwtUtil jwtUtil;
    private final PhotoUploadService photoUploadService;

    private static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])(?=\\S+$).{8,}$";
    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    public User registerUser(String username, String email, String password, Role role, Date birthdate) {
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
                .build();
        userRepository.save(user);
        emailService.sendVerificationEmail(email, user.getVerificationToken());
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    public User findUserByEmail(String email) {
        return userRepository.findUserByEmail(email);
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

    public User updateProfile(String email, String username, String currentPassword, String newPassword, Date birthdate, Boolean twoFactorEnabled) {
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
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                throw new IllegalArgumentException("New password cannot be the same as the current password");
            }
            validatePassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        user.setBirthdate(birthdate);
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

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User with ID " + id + " not found"));
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

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
        user.setVerificationToken(updatedUser.getVerificationToken());
        user.setVerified(updatedUser.getVerified());

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Set owner to null for all contacts associated with this user
        contactService.unassignContactsFromUser(id);

        // 2. Delete associated LoginHistory records (if not already cascaded)
        if (user.getLoginHistory() != null) {
            user.getLoginHistory().clear(); // Clear the list to trigger cascade deletion
        }

        // 3. Delete the user's profile photo if it exists
        if (user.getProfilePhotoUrl() != null) {
            photoUploadService.deletePhoto(user.getProfilePhotoUrl());
        }

        // 4. Delete the user (JPA will cascade to LoginHistory if cascade = CascadeType.ALL is set)
        userRepository.delete(user);
    }

    public User changeUserPassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (currentPassword == null || currentPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Current password is required");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("New password is required");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password cannot be the same as the current password");
        }

        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    private void validatePassword(String password) {
        if (!pattern.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters long, include an uppercase letter, lowercase letter, number, and special character (!@#$%^&*)");
        }
    }

    public User updateProfilePhoto(String email, MultipartFile photo) throws IOException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getProfilePhotoUrl() != null) {
            photoUploadService.deletePhoto(user.getProfilePhotoUrl());
        }

        String photoUrl = photoUploadService.uploadProfilePhoto(photo);
        user.setProfilePhotoUrl(photoUrl);
        return userRepository.save(user);
    }

    public User setDefaultAvatar(String email, String avatarUrl) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getProfilePhotoUrl() != null) {
            photoUploadService.deletePhoto(user.getProfilePhotoUrl());
        }

        // Ensure no double slashes by normalizing the path
        String normalizedAvatarUrl = "/avatars/" + avatarUrl.replaceAll("^/+", ""); // Remove leading slashes from avatarUrl
        user.setProfilePhotoUrl(normalizedAvatarUrl);
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    // Find multiple users by a list of IDs
    public List<User> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of(); // Return an empty list if the input list is null or empty
        }
        return userRepository.findAllById(ids).stream()
                .filter(user -> ids.contains(user.getId()))
                .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<User> getUsersByIds(List<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .filter(user -> userIds.contains(user.getId()))
                .collect(Collectors.toList());
    }
}