package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.Contact;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Repository.ContactRepository;
import crm.dopaflow_backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
    }

    private LocalDateTime parseDate(String dateStr, boolean isStart) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return isStart ? LocalDateTime.of(1900, 1, 1, 0, 0) : LocalDateTime.of(2099, 12, 31, 23, 59);
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return isStart ? date.atStartOfDay() : date.atTime(LocalTime.MAX);
        } catch (Exception e) {
            System.out.println("Invalid date format: " + dateStr + ", defaulting to " + (isStart ? "1900-01-01" : "2099-12-31"));
            return isStart ? LocalDateTime.of(1900, 1, 1, 0, 0) : LocalDateTime.of(2099, 12, 31, 23, 59);
        }
    }

    public Page<Contact> getAllContacts(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        System.out.println("Fetching all contacts with page=" + page + ", size=" + size + ", sort=" + sort);
        return contactRepository.findAll(pageable);
    }

    public Page<Contact> searchContacts(String query, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        System.out.println("Searching contacts with query=" + query + ", page=" + page + ", size=" + size + ", sort=" + sort);
        return contactRepository.findByNameContainingIgnoreCase(query, pageable);
    }

    public Page<Contact> filterContacts(String status, String startDateStr, String endDateStr, Long ownerId, boolean unassignedOnly, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        LocalDateTime startDate = parseDate(startDateStr, true);
        LocalDateTime endDate = parseDate(endDateStr, false);
        String filteredStatus = (status != null && !status.trim().isEmpty()) ? status : "ANY"; // Use "ANY" instead of null

        System.out.println("Filtering contacts: status=" + filteredStatus + ", startDate=" + startDate + ", endDate=" + endDate +
                ", ownerId=" + ownerId + ", unassignedOnly=" + unassignedOnly + ", page=" + page + ", size=" + size + ", sort=" + sort);

        Page<Contact> result;
        if ("ANY".equals(filteredStatus)) {
            // If status is "ANY", ignore status filter and fetch based on other conditions
            if (unassignedOnly) {
                System.out.println("Unassigned with any status");
                result = contactRepository.findByOwnerIsNullAndCreatedAtBetween(startDate, endDate, pageable);
            } else if (ownerId != null) {
                System.out.println("Specific owner with any status");
                result = contactRepository.findByOwnerIdAndCreatedAtBetween(ownerId, startDate, endDate, pageable);
            } else {
                System.out.println("No specific status or owner, fetching all contacts within date range");
                result = contactRepository.findByCreatedAtBetween(startDate, endDate, pageable);
            }
        } else {
            // Specific status filter
            if (unassignedOnly) {
                result = contactRepository.findByStatusAndOwnerIsNullAndCreatedAtBetween(filteredStatus, startDate, endDate, pageable);
            } else if (ownerId != null) {
                result = contactRepository.findByStatusAndOwnerIdAndCreatedAtBetween(filteredStatus, ownerId, startDate, endDate, pageable);
            } else {
                result = contactRepository.findByStatusAndCreatedAtBetween(filteredStatus, startDate, endDate, pageable);
            }
        }

        System.out.println("Filter result: " + result.getTotalElements() + " contacts found");
        return result;
    }

    public Contact getContact(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
    }

    public Contact createContact(Contact contact) {
        if (contact.getOwner() != null && contact.getOwner().getId() != null) {
            User owner = userRepository.findById(contact.getOwner().getId())
                    .orElseThrow(() -> new RuntimeException("Owner not found"));
            contact.setOwner(owner);
        }
        contact.setCreatedAt(LocalDateTime.now());
        contact.setLastActivity(LocalDateTime.now());
        return contactRepository.save(contact);
    }

    public Contact updateContact(Long id, Contact contactDetails) {
        Contact existingContact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        existingContact.setName(contactDetails.getName());
        existingContact.setEmail(contactDetails.getEmail());
        existingContact.setPhone(contactDetails.getPhone());
        existingContact.setStatus(contactDetails.getStatus());
        existingContact.setCompany(contactDetails.getCompany());
        existingContact.setNotes(contactDetails.getNotes());
        existingContact.setPhotoUrl(contactDetails.getPhotoUrl());
        existingContact.setLastActivity(LocalDateTime.now());

        if (contactDetails.getOwner() != null && contactDetails.getOwner().getId() != null) {
            User owner = userRepository.findById(contactDetails.getOwner().getId())
                    .orElseThrow(() -> new RuntimeException("Owner not found"));
            existingContact.setOwner(owner);
        } else if (contactDetails.getOwner() == null) {
            existingContact.setOwner(null);
        }

        return contactRepository.save(existingContact);
    }

    public void deleteContact(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
        contactRepository.delete(contact);
    }

    public byte[] exportContactsToCsv(List<String> selectedColumns) {
        List<Contact> contacts = contactRepository.findAll();
        StringBuilder csv = new StringBuilder();

        String header = selectedColumns.stream()
                .map(col -> switch (col) {
                    case "name" -> "Name";
                    case "email" -> "Email";
                    case "phone" -> "Phone Number";
                    case "status" -> "Lead Status";
                    case "createdAt" -> "Creation Date";
                    case "owner" -> "Contact Owner";
                    case "company" -> "Company";
                    case "notes" -> "Notes";
                    case "photoUrl" -> "Photo URL";
                    default -> "";
                })
                .collect(Collectors.joining(","));
        csv.append(header).append("\n");

        for (Contact contact : contacts) {
            String row = selectedColumns.stream()
                    .map(col -> switch (col) {
                        case "name" -> escapeCsv(contact.getName());
                        case "email" -> escapeCsv(contact.getEmail());
                        case "phone" -> escapeCsv(contact.getPhone());
                        case "status" -> escapeCsv(contact.getStatus());
                        case "createdAt" -> contact.getCreatedAt() != null ? escapeCsv(contact.getCreatedAt().toString()) : "";
                        case "owner" -> contact.getOwner() != null ? escapeCsv(contact.getOwner().getUsername()) : "";
                        case "company" -> escapeCsv(contact.getCompany());
                        case "notes" -> escapeCsv(contact.getNotes());
                        case "photoUrl" -> escapeCsv(contact.getPhotoUrl());
                        default -> "";
                    })
                    .collect(Collectors.joining(","));
            csv.append(row).append("\n");
        }
        return csv.toString().getBytes();
    }
    public List<Contact> bulkCreateContacts(List<Contact> contacts) {
        return contactRepository.saveAll(contacts.stream().map(contact -> {
            // Set default values if null/empty
            if (contact.getName() == null || contact.getName().trim().isEmpty()) {
                contact.setName(null); // Allow null for flexibility
            }
            if (contact.getEmail() == null || contact.getEmail().trim().isEmpty()) {
                contact.setEmail(null); // Allow null for flexibility
            }
            if (contact.getPhone() == null || contact.getPhone().trim().isEmpty()) {
                contact.setPhone(null); // Allow null for flexibility
            }
            if (contact.getStatus() == null || contact.getStatus().trim().isEmpty()) {
                contact.setStatus("Open"); // Allow null for flexibility
            }
            if (contact.getCompany() == null || contact.getCompany().trim().isEmpty()) {
                contact.setCompany(null); // Allow null for flexibility
            }
            if (contact.getNotes() == null || contact.getNotes().trim().isEmpty()) {
                contact.setNotes(null); // Allow null for flexibility
            }

            // Set creation and last activity timestamps
            contact.setCreatedAt(LocalDateTime.now());
            contact.setLastActivity(LocalDateTime.now());

            // Handle owner by username if provided
            if (contact.getOwnerUsername() != null && !contact.getOwnerUsername().trim().isEmpty()) {
                User owner = userRepository.findByUsername(contact.getOwnerUsername()).orElse(null);
                contact.setOwner(owner);
            }
            contact.setOwnerUsername(null); // Clear temporary username field after mapping

            return contact;
        }).collect(Collectors.toList()));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
    private String escapeCsv(String value) {
        return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"";
    }
}