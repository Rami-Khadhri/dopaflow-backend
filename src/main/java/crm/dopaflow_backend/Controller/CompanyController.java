package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.Model.Company;
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
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyService companyService;
    private static final String UPLOAD_DIR = "uploads/company-photos/";

    @PostMapping("/{companyId}/uploadPhoto")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long companyId, @RequestParam("file") MultipartFile file) {
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

            return ResponseEntity.ok(Map.of("photoUrl", photoUrl));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Photo upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Page<Company>> getAllCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        try {
            Page<Company> companiesPage = companyService.getAllCompanies(page, size, sort);
            if (companiesPage == null) {
                return ResponseEntity.ok(Page.empty());
            }
            return ResponseEntity.ok(companiesPage);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Page.empty());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Company>> searchCompanies(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        return ResponseEntity.ok(companyService.searchCompanies(query, page, size, sort));
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<Company>> filterCompanies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        return ResponseEntity.ok(companyService.filterCompanies(status, ownerId, unassignedOnly, page, size, sort));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<Company> getCompany(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.getCompany(id));
    }

    @PostMapping("/add")
    public ResponseEntity<Company> createCompany(@RequestBody Company company) {
        return ResponseEntity.ok(companyService.createCompany(company));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Company> updateCompany(@PathVariable Long id, @RequestBody Company companyDetails) {
        return ResponseEntity.ok(companyService.updateCompany(id, companyDetails));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importCompanies(@RequestParam("file") MultipartFile file,
                                                               @RequestParam("type") String fileType) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Company> companies;
            if ("csv".equalsIgnoreCase(fileType)) {
                companies = parseCsv(file);
            } else if ("excel".equalsIgnoreCase(fileType)) {
                companies = parseExcel(file);
            } else {
                throw new IllegalArgumentException("Invalid file type. Use 'csv' or 'excel'.");
            }
            List<Company> savedCompanies = companyService.bulkCreateCompanies(companies);
            response.put("message", "Imported " + savedCompanies.size() + " companies from " + companies.size() + " parsed rows");
            response.put("unmappedFields", getUnmappedFields());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Import processed with issues: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } finally {
            unmappedFields.remove();
        }
    }

    private List<Company> parseCsv(MultipartFile file) throws IOException {
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

            for (CSVRecord record : csvParser) {
                Company company = new Company();
                boolean hasName = false;

                for (String header : csvParser.getHeaderNames()) {
                    String value = record.isSet(header) ? record.get(header).trim() : "";
                    String mappedField = fieldMappings.getOrDefault(header.toLowerCase(), null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                if (!value.isEmpty()) {
                                    company.setName(value);
                                    hasName = true;
                                }
                                break;
                            case "email":
                                if (!value.isEmpty() && EMAIL_PATTERN.matcher(value).matches()) {
                                    company.setEmail(value);
                                }
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (!normalizedPhone.isEmpty() && PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                                    company.setPhone(normalizedPhone);
                                }
                                break;
                            case "status":
                                if (!value.isEmpty()) {
                                    company.setStatus(value);
                                }
                                break;
                            case "address":
                                if (!value.isEmpty()) {
                                    company.setAddress(value);
                                }
                                break;
                            case "website":
                                if (!value.isEmpty()) {
                                    company.setWebsite(value);
                                }
                                break;
                            case "industry":
                                if (!value.isEmpty()) {
                                    company.setIndustry(value);
                                }
                                break;
                            case "notes":
                                if (!value.isEmpty()) {
                                    company.setNotes(value);
                                }
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    company.setOwnerUsername(value);
                                }
                                break;
                        }
                    } else if (!value.isEmpty()) {
                        String notes = company.getNotes() != null ? company.getNotes() + "; " : "";
                        company.setNotes(notes + header + ": " + value);
                    }
                }

                if (hasName) {
                    companies.add(company);
                }
            }
            return companies;
        }
    }

    private List<Company> parseExcel(MultipartFile file) throws IOException {
        final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);
        final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Company> companies = new ArrayList<>();
            Set<String> unmappedFields = new HashSet<>();
            Map<String, String> fieldMappings = new HashMap<>();
            Set<String> nameHints = Set.of("name", "company", "business", "org", "organization");

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
                    } else if (header.contains("address")) {
                        fieldMappings.put(header, "address");
                    } else if (header.contains("website") || header.contains("url")) {
                        fieldMappings.put(header, "website");
                    } else if (header.contains("industry")) {
                        fieldMappings.put(header, "industry");
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
                Company company = new Company();
                boolean hasName = false;

                for (Cell cell : row) {
                    String header = headerRow.getCell(cell.getColumnIndex()).getStringCellValue().trim().toLowerCase();
                    String value = getCellValue(cell);
                    String mappedField = fieldMappings.getOrDefault(header, null);

                    if (mappedField != null) {
                        switch (mappedField) {
                            case "name":
                                if (!value.isEmpty()) {
                                    company.setName(value);
                                    hasName = true;
                                }
                                break;
                            case "email":
                                if (!value.isEmpty() && EMAIL_PATTERN.matcher(value).matches()) {
                                    company.setEmail(value);
                                }
                                break;
                            case "phone":
                                String normalizedPhone = value.replaceAll("[^0-9+]", "");
                                if (!normalizedPhone.isEmpty() && PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                                    company.setPhone(normalizedPhone);
                                }
                                break;
                            case "status":
                                if (!value.isEmpty()) {
                                    company.setStatus(value);
                                }
                                break;
                            case "address":
                                if (!value.isEmpty()) {
                                    company.setAddress(value);
                                }
                                break;
                            case "website":
                                if (!value.isEmpty()) {
                                    company.setWebsite(value);
                                }
                                break;
                            case "industry":
                                if (!value.isEmpty()) {
                                    company.setIndustry(value);
                                }
                                break;
                            case "notes":
                                if (!value.isEmpty()) {
                                    company.setNotes(value);
                                }
                                break;
                            case "ownerUsername":
                                if (!value.isEmpty()) {
                                    company.setOwnerUsername(value);
                                }
                                break;
                        }
                    } else if (!value.isEmpty()) {
                        String notes = company.getNotes() != null ? company.getNotes() + "; " : "";
                        company.setNotes(notes + header + ": " + value);
                    }
                }

                if (hasName) {
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

    private void setUnmappedFields(Set<String> fields) {
        unmappedFields.set(fields);
    }

    private Set<String> getUnmappedFields() {
        return unmappedFields.get() != null ? new HashSet<>(unmappedFields.get()) : new HashSet<>();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCompanies(@RequestParam("columns") String columns,
                                                  @RequestParam("type") String fileType) throws IOException {
        List<String> columnList = Arrays.asList(columns.split(","));
        byte[] data = companyService.exportCompaniesToCsv(columnList);
        String filename = "companies.csv";

        if ("excel".equalsIgnoreCase(fileType)) {
            data = companyService.exportCompaniesToExcel(columnList);
            filename = "companies.xlsx";
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(data);
    }
}