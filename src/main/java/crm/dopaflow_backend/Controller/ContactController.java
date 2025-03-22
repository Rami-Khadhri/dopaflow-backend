package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Company;
import crm.dopaflow_backend.Model.Contact;
import crm.dopaflow_backend.Service.ContactService;
import crm.dopaflow_backend.Service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
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
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;
    private final CompanyService companyService; // Added dependency
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
        try {
            Page<Contact> contactsPage = contactService.getAllContacts(page, size, sort);
            return ResponseEntity.ok(contactsPage != null ? contactsPage : Page.empty());
        } catch (Exception e) {
            e.printStackTrace(); // Replace with proper logging
            return ResponseEntity.status(500).body(Page.empty());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Contact>> searchContacts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        try {
            Page<Contact> contactsPage = contactService.searchContacts(query, page, size, sort);
            return ResponseEntity.ok(contactsPage != null ? contactsPage : Page.empty());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Page.empty());
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<Contact>> filterContacts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        try {
            Page<Contact> contactsPage = contactService.filterContacts(status, startDate, endDate, ownerId, unassignedOnly, companyId, page, size, sort);
            return ResponseEntity.ok(contactsPage != null ? contactsPage : Page.empty());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Page.empty());
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<Contact> getContact(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(contactService.getContact(id));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(null);
        }
    }

    @PostMapping("/add")
    public ResponseEntity<Contact> createContact(@RequestBody Contact contact) {
        try {
            return ResponseEntity.ok(contactService.createContact(contact));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(null);
        }
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Contact> updateContact(@PathVariable Long id, @RequestBody Contact contactDetails) {
        try {
            return ResponseEntity.ok(contactService.updateContact(id, contactDetails));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(null);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        try {
            contactService.deleteContact(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(404).build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importContacts(@RequestParam("file") MultipartFile file, @RequestParam("type") String fileType) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("No file uploaded.");
            }
            List<Contact> contacts;
            if ("csv".equalsIgnoreCase(fileType)) {
                contacts = parseCsv(file);
            } else if ("excel".equalsIgnoreCase(fileType)) {
                contacts = parseExcel(file);
            } else {
                throw new IllegalArgumentException("Invalid file type. Use 'csv' or 'excel'.");
            }
            List<Contact> savedContacts = contactService.bulkCreateContacts(contacts);
            response.put("message", "Imported " + savedContacts.size() + " contacts from " + contacts.size() + " parsed rows");
            response.put("unmappedFields", getUnmappedFields());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Import processed with issues: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } finally {
            unmappedFields.remove();
        }
    }

    private List<Contact> parseCsv(MultipartFile file) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader()
                     .withTrim()
                     .withAllowMissingColumnNames()
                     .withIgnoreEmptyLines()
                     .withIgnoreHeaderCase())) {

            List<Contact> contacts = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "first", "last", "full", "surname", "given", "username", "thename");

            if (csvParser.getHeaderNames() != null) {
                for (String header : csvParser.getHeaderNames()) {
                    String lowerHeader = header.toLowerCase();
                    if (nameHints.stream().anyMatch(lowerHeader::contains)) {
                        fieldMappings.put(lowerHeader, "name");
                    } else if (lowerHeader.contains("email")) {
                        fieldMappings.put(lowerHeader, "email");
                    } else if (lowerHeader.contains("phone") || lowerHeader.contains("mobile")) {
                        fieldMappings.put(lowerHeader, "phone");
                    } else if (lowerHeader.contains("status")) {
                        fieldMappings.put(lowerHeader, "status");
                    } else if (lowerHeader.contains("company")) {
                        fieldMappings.put(lowerHeader, "company");
                    } else if (lowerHeader.contains("notes")) {
                        fieldMappings.put(lowerHeader, "notes");
                    } else if (lowerHeader.contains("owner")) {
                        fieldMappings.put(lowerHeader, "ownerUsername");
                    } else {
                        unmappedFields.add(header);
                    }
                }
            }
            setUnmappedFields(unmappedFields);

            for (CSVRecord record : csvParser) {
                Contact contact = new Contact();
                boolean hasName = false;

                for (String header : csvParser.getHeaderNames()) {
                    String value = record.isSet(header) ? record.get(header).trim() : "";
                    String mappedField = fieldMappings.getOrDefault(header.toLowerCase(), null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                if (!value.isEmpty()) {
                                    contact.setName(value);
                                    hasName = true;
                                }
                                break;
                            case "email":
                                if (!value.isEmpty() && EMAIL_PATTERN.matcher(value).matches()) {
                                    contact.setEmail(value);
                                } else if (!value.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid email: " + value);
                                }
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (!normalizedPhone.isEmpty() && PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                                    contact.setPhone(normalizedPhone);
                                } else if (!normalizedPhone.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid phone: " + value);
                                }
                                break;
                            case "status":
                                if (!value.isEmpty()) {
                                    contact.setStatus(value.equalsIgnoreCase("open") || value.equalsIgnoreCase("closed") ? value : "Open");
                                }
                                break;
                            case "company":
                                if (!value.isEmpty()) {
                                    Company company = new Company();
                                    company.setName(value);
                                    // Delegate to CompanyService to ensure all required fields are set
                                    company = companyService.createCompany(company);
                                    contact.setCompany(company);
                                }
                                break;
                            case "notes":
                                if (!value.isEmpty()) {
                                    contact.setNotes(value);
                                }
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    contact.setOwnerUsername(value);
                                }
                                break;
                        }
                    } else if (!value.isEmpty()) {
                        String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                        contact.setNotes(notes + header + ": " + value);
                    }
                }

                if (hasName) {
                    if (contact.getEmail() == null) {
                        contact.setEmail("unknown_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
                    }
                    contacts.add(contact);
                }
            }
            return contacts;
        }
    }

    private List<Contact> parseExcel(MultipartFile file) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Contact> contacts = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "first", "last", "full", "surname", "given", "username", "thename");

            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String header = cell.getStringCellValue().trim().toLowerCase();
                    if (nameHints.stream().anyMatch(header::contains)) {
                        fieldMappings.put(header, "name");
                    } else if (header.contains("email")) {
                        fieldMappings.put(header, "email");
                    } else if (header.contains("phone") || header.contains("mobile")) {
                        fieldMappings.put(header, "phone");
                    } else if (header.contains("status")) {
                        fieldMappings.put(header, "status");
                    } else if (header.contains("company")) {
                        fieldMappings.put(header, "company");
                    } else if (header.contains("notes")) {
                        fieldMappings.put(header, "notes");
                    } else if (header.contains("owner")) {
                        fieldMappings.put(header, "ownerUsername");
                    } else {
                        unmappedFields.add(header);
                    }
                }
            }
            setUnmappedFields(unmappedFields);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Contact contact = new Contact();
                boolean hasName = false;

                for (Cell cell : row) {
                    String header = headerRow.getCell(cell.getColumnIndex()).getStringCellValue().trim().toLowerCase();
                    String value = getCellValue(cell);
                    String mappedField = fieldMappings.getOrDefault(header, null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                if (!value.isEmpty()) {
                                    contact.setName(value);
                                    hasName = true;
                                }
                                break;
                            case "email":
                                if (!value.isEmpty() && EMAIL_PATTERN.matcher(value).matches()) {
                                    contact.setEmail(value);
                                } else if (!value.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid email: " + value);
                                }
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (!normalizedPhone.isEmpty() && PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                                    contact.setPhone(normalizedPhone);
                                } else if (!normalizedPhone.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid phone: " + value);
                                }
                                break;
                            case "status":
                                if (!value.isEmpty()) {
                                    contact.setStatus(value.equalsIgnoreCase("open") || value.equalsIgnoreCase("closed") ? value : "Open");
                                }
                                break;
                            case "company":
                                if (!value.isEmpty()) {
                                    Company company = new Company();
                                    company.setName(value);
                                    // Delegate to CompanyService to ensure all required fields are set
                                    company = companyService.createCompany(company);
                                    contact.setCompany(company);
                                }
                                break;
                            case "notes":
                                if (!value.isEmpty()) {
                                    contact.setNotes(value);
                                }
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    contact.setOwnerUsername(value);
                                }
                                break;
                        }
                    } else if (!value.isEmpty()) {
                        String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                        contact.setNotes(notes + header + ": " + value);
                    }
                }

                if (hasName) {
                    if (contact.getEmail() == null) {
                        contact.setEmail("unknown_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
                    }
                    contacts.add(contact);
                }
            }
            return contacts;
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    private ThreadLocal<Set<String>> unmappedFields = new ThreadLocal<>();

    private void setUnmappedFields(Set<String> fields) {
        unmappedFields.set(fields);
    }

    private Set<String> getUnmappedFields() {
        return unmappedFields.get() != null ? new HashSet<>(unmappedFields.get()) : new HashSet<>();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportContacts(@RequestParam("columns") String columns, @RequestParam("type") String fileType) throws IOException {
        try {
            List<String> columnList = Arrays.asList(columns.split(","));
            byte[] data;
            String filename;

            if ("csv".equalsIgnoreCase(fileType)) {
                data = contactService.exportContactsToCsv(columnList);
                filename = "contacts.csv";
            } else if ("excel".equalsIgnoreCase(fileType)) {
                data = contactService.exportContactsToExcel(columnList);
                filename = "contacts.xlsx";
            } else {
                throw new IllegalArgumentException("Invalid file type. Use 'csv' or 'excel'.");
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}