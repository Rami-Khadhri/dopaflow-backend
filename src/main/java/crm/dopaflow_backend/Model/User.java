// User.java (Updated)
package crm.dopaflow_backend.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Date birthdate;

    @Enumerated(EnumType.STRING)
    private StatutUser status;

    @Column(nullable = false)
    private Date lastLogin;

    @Column(nullable = false)
    private Boolean verified;

    private String verificationToken;

    @Column(name = "two_factor_enabled")
    private boolean twoFactorEnabled;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl; // New field for profile photo URL

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private List<LoginHistory> loginHistory = new ArrayList<>();
    @Column(name = "last_active") // New field for last active timestamp
    private Instant lastActive;
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    public User(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public User(String username, String email, String password, Date birthdate) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.birthdate = birthdate;
    }

    @Override
    public boolean isEnabled() {
        return this.verified;
    }

    public void addLoginHistory(LoginHistory history) {
        loginHistory.add(history);
        history.setUser(this);
    }
    public void deleteLoginHistory() {
        if (loginHistory != null) {
            loginHistory.clear();
        }
    }

}