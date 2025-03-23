package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.Opportunity;
import crm.dopaflow_backend.Model.Stage;
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
        return opportunityRepository.save(opportunity);
    }

    public Opportunity updateOpportunity(Long id, Opportunity opportunityDetails) {
        Opportunity opportunity = getOpportunity(id);

        // Only update fields that are provided and not null
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
        return opportunityRepository.save(opportunity);
    }

    public Opportunity decrementProgress(Long id, int decrement) {
        Opportunity opportunity = getOpportunity(id);
        int newProgress = Math.min(100, Math.max(0, opportunity.getProgress() - decrement));
        opportunity.setProgress(newProgress);
        return opportunityRepository.save(opportunity);
    }

    public Opportunity changeStage(Long id, String stage) {
        Opportunity opportunity = getOpportunity(id);
        try {
            Stage newStage = Stage.valueOf(stage.toUpperCase());
            opportunity.setStage(newStage);
            return opportunityRepository.save(opportunity);
        } catch (IllegalArgumentException e) {
            String validStages = String.join(", ",
                    java.util.Arrays.stream(Stage.values())
                            .map(Enum::name)
                            .toArray(String[]::new));
            throw new IllegalArgumentException("Invalid stage value: " + stage + ". Must be one of: " + validStages);
        }
    }

    // New method to assign a contact to an existing opportunity
    public Opportunity assignContact(Long id, Long contactId) {
        Opportunity opportunity = getOpportunity(id);
        opportunity.setContact(contactId != null ? contactRepository.findContactById(contactId) : null); // Assuming Contact has a constructor with ID
        return opportunityRepository.save(opportunity);
    }
}