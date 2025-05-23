package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Opportunity;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.OpportunityService;
import crm.dopaflow_backend.Service.UserService;
import crm.dopaflow_backend.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
public class OpportunityController {
    private final OpportunityService opportunityService;
    private final UserService userService; // Add UserService dependency
    private final JwtUtil jwtUtil;         // Add JwtUtil dependency

    @GetMapping("/all")
    public ResponseEntity<Page<Opportunity>> getAllOpportunities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(opportunityService.getAllOpportunities(page, size, sort));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<Opportunity> getOpportunity(@PathVariable Long id) {
        return ResponseEntity.ok(opportunityService.getOpportunity(id));
    }

    @PostMapping("/add")
    public ResponseEntity<Opportunity> createOpportunity(
            @RequestBody Opportunity opportunity,
            @RequestHeader("Authorization") String authHeader) { // Use token from header
        // Extract email from JWT token
        String email = jwtUtil.getEmailFromToken(authHeader);
        // Find user by email
        User owner = userService.getUserByEmail(email);
        if (owner == null) {
            throw new RuntimeException("User not found for email: " + email);
        }
        // Set only the owner's ID
        opportunity.setOwner(owner);
        // Set default values if null
        if (opportunity.getProgress() == null) {
            opportunity.setProgress(0);
        }
        if (opportunity.getValue() == null) {
            opportunity.setValue(BigDecimal.ZERO);
        }
        if (opportunity.getStatus() == null) {
            opportunity.setStatus(crm.dopaflow_backend.Model.StatutOpportunity.IN_PROGRESS);
        }
        return ResponseEntity.ok(opportunityService.createOpportunity(opportunity));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Opportunity> updateOpportunity(
            @PathVariable Long id,
            @RequestBody Opportunity opportunityDetails) {
        return ResponseEntity.ok(opportunityService.updateOpportunity(id, opportunityDetails));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteOpportunity(@PathVariable Long id) {
        opportunityService.deleteOpportunity(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/increment-progress/{id}")
    public ResponseEntity<Opportunity> incrementProgress(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int increment) {
        return ResponseEntity.ok(opportunityService.incrementProgress(id, increment));
    }

    @PutMapping("/decrement-progress/{id}")
    public ResponseEntity<Opportunity> decrementProgress(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int decrement) {
        return ResponseEntity.ok(opportunityService.decrementProgress(id, decrement));
    }

    @PutMapping("/change-stage/{id}")
    public ResponseEntity<Opportunity> changeStage(
            @PathVariable Long id,
            @RequestParam String stage) {
        return ResponseEntity.ok(opportunityService.changeStage(id, stage));
    }

    @PutMapping("/assign/{id}")
    public ResponseEntity<Opportunity> assignContact(
            @PathVariable Long id,
            @RequestParam Long contactId) {
        return ResponseEntity.ok(opportunityService.assignContact(id, contactId));
    }
}