package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Company;
import crm.dopaflow_backend.Model.Contact;
import crm.dopaflow_backend.Service.ContactService;
import crm.dopaflow_backend.Service.CompanyService;
import crm.dopaflow_backend.Service.ImportResult;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;
    private final CompanyService companyService;
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
            e.printStackTrace();
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

    // Updated Preview Endpoint
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewContacts(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String fileType) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("No file uploaded.");
            }

            List<String> headers;
            List<Map<String, Object>> contacts;
            Set<String> unmappedFields;

            if ("csv".equalsIgnoreCase(fileType)) {
                ParseResult parseResult = parseCsv(file);
                headers = parseResult.headers;
                contacts = parseResult.contacts;
                unmappedFields = parseResult.unmappedFields;
            } else if ("excel".equalsIgnoreCase(fileType)) {
                ParseResult parseResult = parseExcel(file);
                headers = parseResult.headers;
                contacts = parseResult.contacts;
                unmappedFields = parseResult.unmappedFields;
            } else {
                throw new IllegalArgumentException("Invalid file type. Use 'csv' or 'excel'.");
            }

            response.put("headers", headers);
            response.put("contacts", contacts);
            response.put("unmappedFields", new ArrayList<>(unmappedFields));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Preview failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importContacts(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String fileType,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting,
            @RequestParam(value = "selectedColumns", required = false) String selectedColumnsJson) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("No file uploaded.");
            }

            // Parse selected columns if provided
            List<String> selectedColumns = selectedColumnsJson != null
                    ? Arrays.asList(selectedColumnsJson.replaceAll("[\\[\\]\"]", "").split(","))
                    : null;

            List<Contact> contacts;
            Set<String> unmappedFields;

            if ("csv".equalsIgnoreCase(fileType)) {
                ParseResult parseResult = parseCsv(file);
                contacts = parseResult.originalContacts;
                unmappedFields = parseResult.unmappedFields;
            } else if ("excel".equalsIgnoreCase(fileType)) {
                ParseResult parseResult = parseExcel(file);
                contacts = parseResult.originalContacts;
                unmappedFields = parseResult.unmappedFields;
            } else {
                throw new IllegalArgumentException("Invalid file type. Use 'csv' or 'excel'.");
            }

            // Filter contacts based on selected columns if provided
            if (selectedColumns != null && !selectedColumns.isEmpty()) {
                contacts = filterContactsBySelectedColumns(contacts, selectedColumns);
            }

            ImportResult<Contact> importResult = contactService.bulkImportContacts(contacts, updateExisting);
            response.put("message", String.format("Imported contacts: %d created, %d updated, %d skipped out of %d parsed rows",
                    importResult.getCreated(), importResult.getUpdated(), importResult.getSkipped(), contacts.size()));
            response.put("unmappedFields", new ArrayList<>(unmappedFields));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Import failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private static class ParseResult {
        List<String> headers;
        List<Map<String, Object>> contacts;
        List<Contact> originalContacts;
        Set<String> unmappedFields;

        ParseResult(List<String> headers, List<Map<String, Object>> contacts, List<Contact> originalContacts, Set<String> unmappedFields) {
            this.headers = headers;
            this.contacts = contacts;
            this.originalContacts = originalContacts;
            this.unmappedFields = unmappedFields;
        }
    }

    private ParseResult parseCsv(MultipartFile file) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withTrim()
                     .withAllowMissingColumnNames()
                     .withIgnoreEmptyLines()
                     .withIgnoreHeaderCase())) {

            List<String> headers = csvParser.getHeaderNames();
            List<Map<String, Object>> contacts = new ArrayList<>();
            List<Contact> originalContacts = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "first", "last", "full", "surname", "given", "username", "thename");

            // Map headers to fields
            for (String header : headers) {
                String lowerHeader = header.toLowerCase();
                if (nameHints.stream().anyMatch(lowerHeader::contains)) {
                    fieldMappings.put(lowerHeader, "name");
                } else if (lowerHeader.contains("email") && !lowerHeader.contains("company")) {
                    fieldMappings.put(lowerHeader, "email");
                } else if ((lowerHeader.contains("phone") || lowerHeader.contains("mobile")) && !lowerHeader.contains("company")) {
                    fieldMappings.put(lowerHeader, "phone");
                } else if (lowerHeader.contains("status") && !lowerHeader.contains("company")) {
                    fieldMappings.put(lowerHeader, "status");
                } else if (lowerHeader.contains("company") && !lowerHeader.contains("email") && !lowerHeader.contains("phone") &&
                        !lowerHeader.contains("status") && !lowerHeader.contains("address") && !lowerHeader.contains("website") &&
                        !lowerHeader.contains("industry") && !lowerHeader.contains("notes") && !lowerHeader.contains("owner") &&
                        !lowerHeader.contains("photo")) {
                    fieldMappings.put(lowerHeader, "company");
                } else if (lowerHeader.contains("notes") && !lowerHeader.contains("company")) {
                    fieldMappings.put(lowerHeader, "notes");
                } else if (lowerHeader.contains("owner") && !lowerHeader.contains("company")) {
                    fieldMappings.put(lowerHeader, "ownerUsername");
                } else {
                    unmappedFields.add(header);
                }
            }

            for (CSVRecord record : csvParser) {
                Contact contact = new Contact();
                Map<String, Object> contactMap = new HashMap<>();
                Company company = null;
                boolean hasName = false;

                for (String header : headers) {
                    String value = record.isSet(header) ? record.get(header).trim() : "";
                    String lowerHeader = header.toLowerCase();
                    String mappedField = fieldMappings.getOrDefault(lowerHeader, null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                if (!value.isEmpty()) {
                                    contact.setName(value);
                                    contactMap.put("name", value);
                                    hasName = true;
                                }
                                break;
                            case "email":
                                if (!value.isEmpty() && EMAIL_PATTERN.matcher(value).matches()) {
                                    contact.setEmail(value);
                                    contactMap.put("email", value);
                                } else if (!value.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid email: " + value);
                                    contactMap.put("email", null);
                                    contactMap.put("notes", contact.getNotes());
                                }
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (!normalizedPhone.isEmpty() && PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                                    contact.setPhone(normalizedPhone);
                                    contactMap.put("phone", normalizedPhone);
                                } else if (!normalizedPhone.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid phone: " + value);
                                    contactMap.put("phone", null);
                                    contactMap.put("notes", contact.getNotes());
                                }
                                break;
                            case "status":
                                if (!value.isEmpty()) {
                                    String status = value.equalsIgnoreCase("open") || value.equalsIgnoreCase("closed") ? value : "Open";
                                    contact.setStatus(status);
                                    contactMap.put("status", status);
                                }
                                break;
                            case "company":
                                if (!value.isEmpty()) {
                                    company = new Company();
                                    company.setName(value);
                                    contact.setCompany(company);
                                    contactMap.put("company", value);
                                }
                                break;
                            case "notes":
                                if (!value.isEmpty()) {
                                    contact.setNotes(value);
                                    contactMap.put("notes", value);
                                }
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    contact.setOwnerUsername(value);
                                    contactMap.put("ownerUsername", value);
                                }
                                break;
                        }
                    } else if (!value.isEmpty()) {
                        String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                        contact.setNotes(notes + header + ": " + value);
                        contactMap.put("notes", contact.getNotes());
                    }
                }

                if (hasName) {
                    if (contact.getEmail() == null) {
                        String dummyEmail = "unknown_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
                        contact.setEmail(dummyEmail);
                        contactMap.put("email", dummyEmail);
                    }
                    contacts.add(contactMap);
                    originalContacts.add(contact);
                }
            }

            return new ParseResult(headers, contacts, originalContacts, unmappedFields);
        }
    }

    private ParseResult parseExcel(MultipartFile file) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            List<Map<String, Object>> contacts = new ArrayList<>();
            List<Contact> originalContacts = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "first", "last", "full", "surname", "given", "username", "thename");

            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String header = cell.getStringCellValue().trim();
                    headers.add(header);
                    String lowerHeader = header.toLowerCase();
                    if (nameHints.stream().anyMatch(lowerHeader::contains)) {
                        fieldMappings.put(lowerHeader, "name");
                    } else if (lowerHeader.contains("email") && !lowerHeader.contains("company")) {
                        fieldMappings.put(lowerHeader, "email");
                    } else if ((lowerHeader.contains("phone") || lowerHeader.contains("mobile")) && !lowerHeader.contains("company")) {
                        fieldMappings.put(lowerHeader, "phone");
                    } else if (lowerHeader.contains("status") && !lowerHeader.contains("company")) {
                        fieldMappings.put(lowerHeader, "status");
                    } else if (lowerHeader.contains("company") && !lowerHeader.contains("email") && !lowerHeader.contains("phone") &&
                            !lowerHeader.contains("status") && !lowerHeader.contains("address") && !lowerHeader.contains("website") &&
                            !lowerHeader.contains("industry") && !lowerHeader.contains("notes") && !lowerHeader.contains("owner") &&
                            !lowerHeader.contains("photo")) {
                        fieldMappings.put(lowerHeader, "company");
                    } else if (lowerHeader.contains("notes") && !lowerHeader.contains("company")) {
                        fieldMappings.put(lowerHeader, "notes");
                    } else if (lowerHeader.contains("owner") && !lowerHeader.contains("company")) {
                        fieldMappings.put(lowerHeader, "ownerUsername");
                    } else {
                        unmappedFields.add(header);
                    }
                }
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Contact contact = new Contact();
                Map<String, Object> contactMap = new HashMap<>();
                Company company = null;
                boolean hasName = false;

                for (Cell cell : row) {
                    String header = headerRow.getCell(cell.getColumnIndex()).getStringCellValue().trim();
                    String lowerHeader = header.toLowerCase();
                    String value = getCellValue(cell);
                    String mappedField = fieldMappings.getOrDefault(lowerHeader, null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                if (!value.isEmpty()) {
                                    contact.setName(value);
                                    contactMap.put("name", value);
                                    hasName = true;
                                }
                                break;
                            case "email":
                                if (!value.isEmpty() && EMAIL_PATTERN.matcher(value).matches()) {
                                    contact.setEmail(value);
                                    contactMap.put("email", value);
                                } else if (!value.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid email: " + value);
                                    contactMap.put("email", null);
                                    contactMap.put("notes", contact.getNotes());
                                }
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (!normalizedPhone.isEmpty() && PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                                    contact.setPhone(normalizedPhone);
                                    contactMap.put("phone", normalizedPhone);
                                } else if (!normalizedPhone.isEmpty()) {
                                    String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                                    contact.setNotes(notes + "Invalid phone: " + value);
                                    contactMap.put("phone", null);
                                    contactMap.put("notes", contact.getNotes());
                                }
                                break;
                            case "status":
                                if (!value.isEmpty()) {
                                    String status = value.equalsIgnoreCase("open") || value.equalsIgnoreCase("closed") ? value : "Open";
                                    contact.setStatus(status);
                                    contactMap.put("status", status);
                                }
                                break;
                            case "company":
                                if (!value.isEmpty()) {
                                    company = new Company();
                                    company.setName(value);
                                    contact.setCompany(company);
                                    contactMap.put("company", value);
                                }
                                break;
                            case "notes":
                                if (!value.isEmpty()) {
                                    contact.setNotes(value);
                                    contactMap.put("notes", value);
                                }
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    contact.setOwnerUsername(value);
                                    contactMap.put("ownerUsername", value);
                                }
                                break;
                        }
                    } else if (!value.isEmpty()) {
                        String notes = contact.getNotes() != null ? contact.getNotes() + "; " : "";
                        contact.setNotes(notes + header + ": " + value);
                        contactMap.put("notes", contact.getNotes());
                    }
                }

                if (hasName) {
                    if (contact.getEmail() == null) {
                        String dummyEmail = "unknown_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
                        contact.setEmail(dummyEmail);
                        contactMap.put("email", dummyEmail);
                    }
                    contacts.add(contactMap);
                    originalContacts.add(contact);
                }
            }

            return new ParseResult(headers, contacts, originalContacts, unmappedFields);
        }
    }

    private List<Contact> filterContactsBySelectedColumns(List<Contact> contacts, List<String> selectedColumns) {
        return contacts.stream().map(contact -> {
            Contact filteredContact = new Contact();
            filteredContact.setName(selectedColumns.contains("name") ? contact.getName() : null);
            filteredContact.setEmail(selectedColumns.contains("email") ? contact.getEmail() : null);
            filteredContact.setPhone(selectedColumns.contains("phone") ? contact.getPhone() : null);
            filteredContact.setStatus(selectedColumns.contains("status") ? contact.getStatus() : null);
            filteredContact.setNotes(selectedColumns.contains("notes") ? contact.getNotes() : null);
            filteredContact.setOwnerUsername(selectedColumns.contains("owner") || selectedColumns.contains("ownerUsername") ? contact.getOwnerUsername() : null);
            if (selectedColumns.contains("company") && contact.getCompany() != null) {
                Company company = new Company();
                company.setName(contact.getCompany().getName());
                filteredContact.setCompany(company);
            }
            return filteredContact;
        }).filter(contact -> contact.getName() != null).collect(Collectors.toList());
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