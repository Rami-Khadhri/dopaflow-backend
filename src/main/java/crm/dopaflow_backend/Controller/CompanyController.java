package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Company;
import crm.dopaflow_backend.Service.CompanyService;
import crm.dopaflow_backend.Service.ImportResult;
import crm.dopaflow_backend.Utils.ProgressTracker;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyService companyService;
    @Autowired
    private final SimpMessagingTemplate messagingTemplate;
    private static final String UPLOAD_DIR = "uploads/company-photos/";

    @PostMapping("/{companyId}/uploadPhoto")
    public ResponseEntity<Map<String, Object>> uploadPhoto(@PathVariable Long companyId, @RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            Company company = companyId != 0 ? companyService.getCompany(companyId) : null;
            Path uploadDir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadDir);

            String fileName = "c" + (company != null ? company.getId() : UUID.randomUUID()) + "_" +
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/company-photos/" + fileName;
            if (company != null) {
                company.setPhotoUrl(photoUrl);
                companyService.updateCompany(company.getId(), company);
            }

            response.put("photoUrl", photoUrl);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("error", "Photo upload failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        try {
            return ResponseEntity.ok(companyService.getAllCompanies(page, size, sort));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch companies: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchCompanies(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        try {
            return ResponseEntity.ok(companyService.searchCompanies(query, page, size, sort));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to search companies: " + e.getMessage()));
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterCompanies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        try {
            return ResponseEntity.ok(companyService.filterCompanies(status, ownerId, unassignedOnly, page, size, sort));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to filter companies: " + e.getMessage()));
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getCompany(@PathVariable Long id) {
        try {
            Company company = companyService.getCompany(id);
            return company != null ? ResponseEntity.ok(company) : ResponseEntity.status(404).body(Map.of("error", "Company not found"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch company: " + e.getMessage()));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<?> createCompany(@RequestBody Company company) {
        try {
            Company created = companyService.createCompany(company);
            return created != null ? ResponseEntity.ok(created) : ResponseEntity.status(400).body(Map.of("error", "Failed to create company"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create company: " + e.getMessage()));
        }
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateCompany(@PathVariable Long id, @RequestBody Company companyDetails) {
        try {
            Company updated = companyService.updateCompany(id, companyDetails);
            return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.status(404).body(Map.of("error", "Company not found"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update company: " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) {
        boolean deleted = companyService.deleteCompany(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    // New endpoint for bulk deletion
    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteCompanies(@RequestBody List<Long> companyIds) {
        boolean deleted = companyService.deleteCompanies(companyIds);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCompanies(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String fileType,
            @RequestParam(value = "preview", defaultValue = "false") boolean preview,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting,
            @RequestParam(value = "columns", required = false) String selectedColumns,
            @RequestParam(value = "sessionId") String sessionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file == null || file.isEmpty()) {
                response.put("error", "No file uploaded.");
                return ResponseEntity.badRequest().body(response);
            }

            List<String> columnsToInclude = selectedColumns != null && !selectedColumns.isEmpty()
                    ? Arrays.asList(selectedColumns.split(","))
                    : null;

            List<Company> companies;
            if ("csv".equalsIgnoreCase(fileType)) {
                companies = parseCsv(file, columnsToInclude);
            } else if ("excel".equalsIgnoreCase(fileType)) {
                companies = parseExcel(file, columnsToInclude);
            } else {
                response.put("error", "Invalid file type. Use 'csv' or 'excel'.");
                return ResponseEntity.badRequest().body(response);
            }

            if (preview) {
                response.put("companies", companies);
                response.put("unmappedFields", getUnmappedFields());
                response.put("headers", getHeaders());
                return ResponseEntity.ok(response);
            } else {
                ProgressTracker progressTracker = new ProgressTracker();
                ImportResult<Company> importResult = companyService.bulkImportCompanies(companies, updateExisting, progressTracker);
                messagingTemplate.convertAndSend("/topic/import-progress/" + sessionId, Map.of("progress", progressTracker.getProgress()));

                response.put("message", String.format("Imported companies: %d created, %d updated, %d skipped out of %d parsed rows",
                        importResult.getCreated(), importResult.getUpdated(), importResult.getSkipped(), companies.size()));
                response.put("unmappedFields", getUnmappedFields());
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            response.put("error", "Import failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } finally {
            unmappedFields.remove();
            headers.remove();
        }
    }

    // In CompanyController.java
    @GetMapping("/allNames")
    public ResponseEntity<List<Map<String, Object>>> getAllCompanyNames() {
        try {
            List<Company> companies = companyService.getTop50Companies(); // New method
            List<Map<String, Object>> companyList = companies.stream()
                    .map(company -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", company.getId());
                        map.put("name", company.getName());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(companyList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }
    @GetMapping(value = "/import-progress/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getImportProgress(@PathVariable String sessionId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        // Implementation for SSE progress updates can be added here if needed
        return emitter;
    }

    private List<Company> parseCsv(MultipartFile file, List<String> columnsToInclude) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader()
                     .withTrim()
                     .withAllowMissingColumnNames()
                     .withIgnoreEmptyLines()
                     .withIgnoreHeaderCase())) {

            List<Company> companies = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "company", "business", "org", "organization");
            List<String> allHeaders = csvParser.getHeaderNames() != null ? new ArrayList<>(csvParser.getHeaderNames()) : new ArrayList<>();

            setHeaders(allHeaders);

            for (String header : allHeaders) {
                String lowerHeader = header.toLowerCase();
                if (columnsToInclude != null && !columnsToInclude.contains(lowerHeader)) {
                    unmappedFields.add(header);
                    continue;
                }
                if (nameHints.stream().anyMatch(lowerHeader::contains)) {
                    fieldMappings.put(lowerHeader, "name");
                } else if (lowerHeader.contains("email")) {
                    fieldMappings.put(lowerHeader, "email");
                } else if (lowerHeader.contains("phone") || lowerHeader.contains("mobile")) {
                    fieldMappings.put(lowerHeader, "phone");
                } else if (lowerHeader.contains("status")) {
                    fieldMappings.put(lowerHeader, "status");
                } else if (lowerHeader.contains("address")) {
                    fieldMappings.put(lowerHeader, "address");
                } else if (lowerHeader.contains("website") || lowerHeader.contains("url")) {
                    fieldMappings.put(lowerHeader, "website");
                } else if (lowerHeader.contains("industry")) {
                    fieldMappings.put(lowerHeader, "industry");
                } else if (lowerHeader.contains("notes")) {
                    fieldMappings.put(lowerHeader, "notes");
                } else if (lowerHeader.contains("owner")) {
                    fieldMappings.put(lowerHeader, "ownerUsername");
                } else {
                    unmappedFields.add(header);
                }
            }
            setUnmappedFields(unmappedFields);

            for (CSVRecord record : csvParser) {
                Company company = new Company();
                boolean hasName = false;
                StringBuilder notes = new StringBuilder();

                for (String header : allHeaders) {
                    String value = record.isSet(header) ? record.get(header).trim() : "";
                    if (value.isEmpty()) continue;

                    String lowerHeader = header.toLowerCase();
                    if (columnsToInclude != null && !columnsToInclude.contains(lowerHeader)) {
                        notes.append(header).append(": ").append(value).append("; ");
                        continue;
                    }

                    String mappedField = fieldMappings.getOrDefault(lowerHeader, null);
                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                company.setName(value);
                                hasName = true;
                                break;
                            case "email":
                                if (EMAIL_PATTERN.matcher(value).matches()) company.setEmail(value);
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (PHONE_PATTERN.matcher(normalizedPhone).matches()) company.setPhone(normalizedPhone);
                                break;
                            case "status":
                                company.setStatus(value);
                                break;
                            case "address":
                                company.setAddress(value);
                                break;
                            case "website":
                                company.setWebsite(value);
                                break;
                            case "industry":
                                company.setIndustry(value);
                                break;
                            case "notes":
                                company.setNotes(value);
                                break;
                            case "ownerUsername":
                                company.setOwnerUsername(value);
                                break;
                        }
                    } else {
                        notes.append(header).append(": ").append(value).append("; ");
                    }
                }

                if (hasName) {
                    if (notes.length() > 0) {
                        company.setNotes(notes.substring(0, notes.length() - 2)); // Remove trailing "; "
                    }
                    companies.add(company);
                }
            }
            return companies;
        }
    }

    private List<Company> parseExcel(MultipartFile file, List<String> columnsToInclude) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Company> companies = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "company", "business", "org", "organization");
            Row headerRow = sheet.getRow(0);
            List<String> allHeaders = new ArrayList<>();

            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    allHeaders.add(cell.getStringCellValue().trim());
                }
            }
            setHeaders(allHeaders);

            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String header = cell.getStringCellValue().trim();
                    String lowerHeader = header.toLowerCase();
                    if (columnsToInclude != null && !columnsToInclude.contains(lowerHeader)) {
                        unmappedFields.add(header);
                        continue;
                    }
                    if (nameHints.stream().anyMatch(lowerHeader::contains)) {
                        fieldMappings.put(lowerHeader, "name");
                    } else if (lowerHeader.contains("email")) {
                        fieldMappings.put(lowerHeader, "email");
                    } else if (lowerHeader.contains("phone") || lowerHeader.contains("mobile")) {
                        fieldMappings.put(lowerHeader, "phone");
                    } else if (lowerHeader.contains("status")) {
                        fieldMappings.put(lowerHeader, "status");
                    } else if (lowerHeader.contains("address")) {
                        fieldMappings.put(lowerHeader, "address");
                    } else if (lowerHeader.contains("website") || lowerHeader.contains("url")) {
                        fieldMappings.put(lowerHeader, "website");
                    } else if (lowerHeader.contains("industry")) {
                        fieldMappings.put(lowerHeader, "industry");
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

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Company company = new Company();
                boolean hasName = false;
                StringBuilder notes = new StringBuilder();

                for (Cell cell : row) {
                    String header = headerRow.getCell(cell.getColumnIndex()).getStringCellValue().trim();
                    String value = getCellValue(cell);
                    if (value.isEmpty()) continue;

                    String lowerHeader = header.toLowerCase();
                    if (columnsToInclude != null && !columnsToInclude.contains(lowerHeader)) {
                        notes.append(header).append(": ").append(value).append("; ");
                        continue;
                    }

                    String mappedField = fieldMappings.getOrDefault(lowerHeader, null);
                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                company.setName(value);
                                hasName = true;
                                break;
                            case "email":
                                if (EMAIL_PATTERN.matcher(value).matches()) company.setEmail(value);
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (PHONE_PATTERN.matcher(normalizedPhone).matches()) company.setPhone(normalizedPhone);
                                break;
                            case "status":
                                company.setStatus(value);
                                break;
                            case "address":
                                company.setAddress(value);
                                break;
                            case "website":
                                company.setWebsite(value);
                                break;
                            case "industry":
                                company.setIndustry(value);
                                break;
                            case "notes":
                                company.setNotes(value);
                                break;
                            case "ownerUsername":
                                company.setOwnerUsername(value);
                                break;
                        }
                    } else {
                        notes.append(header).append(": ").append(value).append("; ");
                    }
                }

                if (hasName) {
                    if (notes.length() > 0) {
                        company.setNotes(notes.substring(0, notes.length() - 2));
                    }
                    companies.add(company);
                }
            }
            return companies;
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
    private ThreadLocal<List<String>> headers = new ThreadLocal<>();

    private void setUnmappedFields(Set<String> fields) {
        unmappedFields.set(fields);
    }

    private Set<String> getUnmappedFields() {
        return unmappedFields.get() != null ? new HashSet<>(unmappedFields.get()) : new HashSet<>();
    }

    private void setHeaders(List<String> headersList) {
        headers.set(headersList);
    }

    private List<String> getHeaders() {
        return headers.get() != null ? new ArrayList<>(headers.get()) : new ArrayList<>();
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportCompanies(
            @RequestParam("columns") String columns,
            @RequestParam("type") String fileType) {
        try {
            List<String> columnList = Arrays.asList(columns.split(","));
            byte[] data = "csv".equalsIgnoreCase(fileType) ?
                    companyService.exportCompaniesToCsv(columnList) :
                    companyService.exportCompaniesToExcel(columnList);
            String filename = "companies." + ("csv".equalsIgnoreCase(fileType) ? "csv" : "xlsx");

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Export failed: " + e.getMessage()));
        }
    }
}