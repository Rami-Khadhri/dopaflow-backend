package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.Opportunity;
import crm.dopaflow_backend.Model.Stage;
import crm.dopaflow_backend.Model.StatutOpportunity;
import crm.dopaflow_backend.Repository.ContactRepository;
import crm.dopaflow_backend.Repository.OpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpportunityService {
    private final OpportunityRepository opportunityRepository;
    private final ContactRepository contactRepository;
    private final TaskService taskService;

    public Page<Opportunity> getAllOpportunities(int page, int size, String sort) {
        Sort sortObj = Sort.by(Sort.Direction.fromString(sort.split(",")[1]), sort.split(",")[0]);
        Pageable pageable = PageRequest.of(page, size, sortObj);
        return opportunityRepository.findAll(pageable);
    }

    public Opportunity getOpportunity(Long id) {
        return opportunityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + id));
    }

    public Opportunity createOpportunity(Opportunity opportunity) {
        // Set default status based on stage
        if (opportunity.getStatus() == null) {
            opportunity.setStatus(opportunity.getStage() == Stage.CLOSED ? StatutOpportunity.WON : StatutOpportunity.IN_PROGRESS);
        }
        validateOpportunity(opportunity);
        return opportunityRepository.save(opportunity);
    }

    public Opportunity updateOpportunity(Long id, Opportunity opportunityDetails) {
        Opportunity opportunity = getOpportunity(id);

        if (opportunityDetails.getTitle() != null) {
            opportunity.setTitle(opportunityDetails.getTitle());
        }
        if (opportunityDetails.getValue() != null) {
            opportunity.setValue(opportunityDetails.getValue());
        }
        if (opportunityDetails.getContact() != null) {
            opportunity.setContact(opportunityDetails.getContact());
        }
        if (opportunityDetails.getPriority() != null) {
            opportunity.setPriority(opportunityDetails.getPriority());
        }
        if (opportunityDetails.getProgress() != null) {
            opportunity.setProgress(opportunityDetails.getProgress());
        }
        if (opportunityDetails.getStage() != null) {
            opportunity.setStage(opportunityDetails.getStage());
        }
        if (opportunityDetails.getOwner() != null) {
            opportunity.setOwner(opportunityDetails.getOwner());
        }
        if (opportunityDetails.getStatus() != null) {
            opportunity.setStatus(opportunityDetails.getStatus());
        }

        validateOpportunity(opportunity);
        return opportunityRepository.save(opportunity);
    }

    public void deleteOpportunity(Long id) {
        Opportunity opportunity = getOpportunity(id);
        taskService.unassignTasksFromOpportunity(id);
        opportunityRepository.delete(opportunity);
    }

    public Opportunity incrementProgress(Long id, int increment) {
        Opportunity opportunity = getOpportunity(id);
        int newProgress = Math.min(100, Math.max(0, opportunity.getProgress() + increment));
        opportunity.setProgress(newProgress);
        validateOpportunity(opportunity);
        return opportunityRepository.save(opportunity);
    }

    public Opportunity decrementProgress(Long id, int decrement) {
        Opportunity opportunity = getOpportunity(id);
        int newProgress = Math.min(100, Math.max(0, opportunity.getProgress() - decrement));
        opportunity.setProgress(newProgress);
        validateOpportunity(opportunity);
        return opportunityRepository.save(opportunity);
    }

    public Opportunity changeStage(Long id, String stage) {
        Opportunity opportunity = getOpportunity(id);
        try {
            Stage newStage = Stage.valueOf(stage.toUpperCase());
            opportunity.setStage(newStage);
            // Automatically set status based on stage
            if (newStage == Stage.CLOSED) {
                // If moving to CLOSED and status isn't WON or LOST, default to WON
                if (opportunity.getStatus() != StatutOpportunity.WON && opportunity.getStatus() != StatutOpportunity.LOST) {
                    opportunity.setStatus(StatutOpportunity.WON);
                }
            } else {
                // For non-CLOSED stages, force IN_PROGRESS
                opportunity.setStatus(StatutOpportunity.IN_PROGRESS);
            }
            validateOpportunity(opportunity);
            return opportunityRepository.save(opportunity);
        } catch (IllegalArgumentException e) {
            String validStages = String.join(", ",
                    java.util.Arrays.stream(Stage.values())
                            .map(Enum::name)
                            .toArray(String[]::new));
            throw new IllegalArgumentException("Invalid stage value: " + stage + ". Must be one of: " + validStages);
        }
    }

    public Opportunity assignContact(Long id, Long contactId) {
        Opportunity opportunity = getOpportunity(id);
        opportunity.setContact(contactId != null ? contactRepository.findContactById(contactId) : null);
        validateOpportunity(opportunity);
        return opportunityRepository.save(opportunity);
    }

    private void validateOpportunity(Opportunity opportunity) {
        if (opportunity.getStage() == Stage.CLOSED) {
            if (opportunity.getStatus() != StatutOpportunity.WON && opportunity.getStatus() != StatutOpportunity.LOST) {
                throw new IllegalArgumentException("Status must be WON or LOST when stage is CLOSED");
            }
        } else if (opportunity.getStatus() != StatutOpportunity.IN_PROGRESS) {
            throw new IllegalArgumentException("Status must be IN_PROGRESS when stage is not CLOSED");
        }
    }
}