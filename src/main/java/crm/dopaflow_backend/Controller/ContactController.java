package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Contact;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class ContactController {
    private final ContactService contactService;
    private static final String UPLOAD_DIR = "uploads/contact-photos/";

    @PostMapping("/{contactId}/uploadPhoto")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long contactId, @RequestParam("file") MultipartFile file) {
        try {
            Contact contact = contactId != 0 ? contactService.getContact(contactId) : null;
            Path uploadDir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadDir);

            String fileName = "c" + (contact != null ? contact.getId() : UUID.randomUUID()) + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/contact-photos/" + fileName;
            if (contact != null) {
                contact.setPhotoUrl(photoUrl);
                contactService.updateContact(contact.getId(), contact);
            }

            return ResponseEntity.ok(Map.of("photoUrl", photoUrl));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Photo upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Page<Contact>> getAllContacts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(contactService.getAllContacts(page, size, sort));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Contact>> searchContacts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(contactService.searchContacts(query, page, size, sort));
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<Contact>> filterContacts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(contactService.filterContacts(status, startDate, endDate, ownerId, unassignedOnly, page, size, sort));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<Contact> getContact(@PathVariable Long id) {
        return ResponseEntity.ok(contactService.getContact(id));
    }

    @PostMapping("/add")
    public ResponseEntity<Contact> createContact(@RequestBody Contact contact) {
        return ResponseEntity.ok(contactService.createContact(contact));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Contact> updateContact(@PathVariable Long id, @RequestBody Contact contactDetails) {
        return ResponseEntity.ok(contactService.updateContact(id, contactDetails));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        contactService.deleteContact(id);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/import/csv")
    public ResponseEntity<Map<String, Object>> importContactsFromCsv(@RequestParam("file") MultipartFile file) {
        try {
            List<Contact> contacts = parseCsv(file);
            List<Contact> savedContacts = contactService.bulkCreateContacts(contacts);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Imported " + savedContacts.size() + " contacts successfully");
            response.put("unmappedFields", getUnmappedFields()); // Track unmapped fields for feedback
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    private List<Contact> parseCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            List<Contact> contacts = new ArrayList<>();
            String line;
            String[] headers = null;

            // Read headers
            if ((line = reader.readLine()) != null) {
                headers = line.split(",");
                for (int i = 0; i < headers.length; i++) {
                    headers[i] = headers[i].trim().replaceAll("^\"|\"$", ""); // Remove quotes
                }
            }

            if (headers == null || headers.length == 0) {
                throw new IOException("No headers found in CSV file");
            }

            // Define possible mappings for Contact fields (case-insensitive)
            Map<String, String> fieldMappings = new HashMap<>();
            fieldMappings.put("name", "name");
            fieldMappings.put("full name", "name"); // Flexible aliases
            fieldMappings.put("first name", "name");
            fieldMappings.put("last name", "name");
            fieldMappings.put("email", "email");
            fieldMappings.put("e-mail", "email");
            fieldMappings.put("phone", "phone");
            fieldMappings.put("phone number", "phone");
            fieldMappings.put("telephone", "phone");
            fieldMappings.put("lead status", "status");
            fieldMappings.put("status", "status");
            fieldMappings.put("company", "company");
            fieldMappings.put("organization", "company");
            fieldMappings.put("notes", "notes");
            fieldMappings.put("comments", "notes");
            fieldMappings.put("contact owner", "ownerUsername"); // Store username for later lookup

            // Track unmapped fields for feedback
            Set<String> unmappedFields = new HashSet<>();
            for (String header : headers) {
                if (!fieldMappings.containsKey(header.toLowerCase())) {
                    unmappedFields.add(header);
                }
            }
            setUnmappedFields(unmappedFields); // Store for response

            // Read data rows
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // Skip empty lines
                String[] values = line.split(",");
                Contact contact = new Contact();

                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String value = values[i].trim().replaceAll("^\"|\"$", ""); // Remove quotes
                    String mappedField = fieldMappings.getOrDefault(headers[i].toLowerCase(), null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                contact.setName(value.isEmpty() ? null : value);
                                break;
                            case "email":
                                contact.setEmail(value.isEmpty() ? null : value);
                                break;
                            case "phone":
                                contact.setPhone(value.isEmpty() ? null : value);
                                break;
                            case "status":
                                contact.setStatus(value.isEmpty() ? null : value);
                                break;
                            case "company":
                                contact.setCompany(value.isEmpty() ? null : value);
                                break;
                            case "notes":
                                contact.setNotes(value.isEmpty() ? null : value);
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    contact.setOwnerUsername(value); // Temporarily store username
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }

                // Basic validation (optional, can be removed for full flexibility)
                contacts.add(contact);
            }

            return contacts;
        }
    }

    // Thread-local storage for unmapped fields (since this is per request)
    private ThreadLocal<Set<String>> unmappedFields = new ThreadLocal<>();

    private void setUnmappedFields(Set<String> fields) {
        unmappedFields.set(fields);
    }

    private Set<String> getUnmappedFields() {
        return unmappedFields.get() != null ? new HashSet<>(unmappedFields.get()) : new HashSet<>();
    }
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportContactsToCsv(@RequestParam("columns") String columns) {
        List<String> columnList = Arrays.asList(columns.split(","));
        byte[] csvData = contactService.exportContactsToCsv(columnList);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=contacts.csv")
                .body(csvData);
    }
}