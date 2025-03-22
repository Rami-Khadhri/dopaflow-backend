package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.Company;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Repository.CompanyRepository;
import crm.dopaflow_backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
    }

    public Page<Company> getAllCompanies(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        return companyRepository.findAll(pageable);
    }

    public Page<Company> searchCompanies(String query, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        return companyRepository.findByNameContainingIgnoreCase(query, pageable);
    }

    public Page<Company> filterCompanies(String status, Long ownerId, boolean unassignedOnly,
                                         int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        String filteredStatus = (status != null && !status.trim().isEmpty()) ? status : "ANY";

        if ("ANY".equals(filteredStatus)) {
            if (unassignedOnly) {
                return companyRepository.findByOwnerIsNull(pageable);
            } else if (ownerId != null) {
                return companyRepository.findByOwnerId(ownerId, pageable);
            } else {
                return companyRepository.findAll(pageable);
            }
        } else {
            if (unassignedOnly) {
                return companyRepository.findByStatusAndOwnerIsNull(filteredStatus, pageable);
            } else if (ownerId != null) {
                return companyRepository.findByStatusAndOwnerId(filteredStatus, ownerId, pageable);
            } else {
                return companyRepository.findByStatus(filteredStatus, pageable);
            }
        }
    }

    public Company getCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
    }

    public Company createCompany(Company company) {
        if (company.getOwner() != null && company.getOwner().getId() != null) {
            User owner = userRepository.findById(company.getOwner().getId())
                    .orElseThrow(() -> new RuntimeException("Owner not found"));
            company.setOwner(owner);
        }
        return companyRepository.save(company);
    }

    public Company updateCompany(Long id, Company companyDetails) {
        Company existingCompany = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        existingCompany.setName(companyDetails.getName());
        existingCompany.setEmail(companyDetails.getEmail());
        existingCompany.setPhone(companyDetails.getPhone());
        existingCompany.setStatus(companyDetails.getStatus());
        existingCompany.setAddress(companyDetails.getAddress());
        existingCompany.setWebsite(companyDetails.getWebsite());
        existingCompany.setIndustry(companyDetails.getIndustry());
        existingCompany.setNotes(companyDetails.getNotes());
        existingCompany.setPhotoUrl(companyDetails.getPhotoUrl());

        if (companyDetails.getOwner() != null && companyDetails.getOwner().getId() != null) {
            User owner = userRepository.findById(companyDetails.getOwner().getId())
                    .orElseThrow(() -> new RuntimeException("Owner not found"));
            existingCompany.setOwner(owner);
        } else if (companyDetails.getOwner() == null) {
            existingCompany.setOwner(null);
        }

        return companyRepository.save(existingCompany);
    }

    public void deleteCompany(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        companyRepository.delete(company);
    }
    public ImportResult<Company> bulkImportCompanies(List<Company> importedCompanies, boolean updateExisting) {
        ImportResult<Company> result = new ImportResult<>();
        List<Company> companiesToSave = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        // Extract owner usernames
        Set<String> ownerUsernames = importedCompanies.stream()
                .map(Company::getOwnerUsername)
                .filter(username -> username != null && !username.trim().isEmpty())
                .collect(Collectors.toSet());

        // Fetch existing users
        List<User> existingUsers = userRepository.findByUsernameIn(new ArrayList<>(ownerUsernames));
        Map<String, User> userMap = existingUsers.stream()
                .collect(Collectors.toMap(User::getUsername, u -> u, (u1, u2) -> u1));

        // Extract company names to check for duplicates
        List<String> companyNames = importedCompanies.stream()
                .map(Company::getName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toList());

        // Fetch existing companies
        List<Company> existingCompanies = companyRepository.findByNameIn(companyNames);
        Map<String, Company> existingCompanyMap = existingCompanies.stream()
                .collect(Collectors.toMap(Company::getName, c -> c, (c1, c2) -> c1));

        for (Company imported : importedCompanies) {
            String name = imported.getName();
            if (name == null || name.trim().isEmpty()) {
                skipped++; // Skip companies without name
                continue;
            }

            Company company;
            if (existingCompanyMap.containsKey(name)) {
                if (updateExisting) {
                    company = existingCompanyMap.get(name);
                    // Update non-null fields only
                    if (imported.getEmail() != null) company.setEmail(imported.getEmail());
                    if (imported.getPhone() != null) company.setPhone(imported.getPhone());
                    if (imported.getStatus() != null) company.setStatus(imported.getStatus());
                    if (imported.getAddress() != null) company.setAddress(imported.getAddress());
                    if (imported.getWebsite() != null) company.setWebsite(imported.getWebsite());
                    if (imported.getIndustry() != null) company.setIndustry(imported.getIndustry());
                    if (imported.getNotes() != null) company.setNotes(imported.getNotes());
                    if (imported.getPhotoUrl() != null) company.setPhotoUrl(imported.getPhotoUrl());
                    // Update owner if provided
                    if (imported.getOwnerUsername() != null && !imported.getOwnerUsername().trim().isEmpty()) {
                        company.setOwner(userMap.get(imported.getOwnerUsername()));
                    }
                    updated++;
                } else {
                    skipped++; // Skip duplicates if not updating
                    continue;
                }
            } else {
                company = new Company();
                company.setName(name);
                company.setEmail(imported.getEmail() != null ? imported.getEmail() : "unknown@dopaflow.com");
                company.setPhone(imported.getPhone() != null ? imported.getPhone() : "N/A");
                company.setStatus(imported.getStatus() != null ? imported.getStatus() : "Active");
                company.setAddress(imported.getAddress() != null ? imported.getAddress() : "N/A");
                company.setWebsite(imported.getWebsite() != null ? imported.getWebsite() : "N/A");
                company.setIndustry(imported.getIndustry() != null ? imported.getIndustry() : "N/A");
                company.setNotes(imported.getNotes());
                company.setPhotoUrl(imported.getPhotoUrl());
                // Set owner if provided
                if (imported.getOwnerUsername() != null && !imported.getOwnerUsername().trim().isEmpty()) {
                    company.setOwner(userMap.get(imported.getOwnerUsername()));
                }
                created++;
            }
            companiesToSave.add(company);
        }

        if (!companiesToSave.isEmpty()) {
            companyRepository.saveAll(companiesToSave);
        }
        result.setSavedEntities(companiesToSave);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        return result;
    }

    public byte[] exportCompaniesToCsv(List<String> selectedColumns) {
        List<Company> companies = companyRepository.findAll();
        StringBuilder csv = new StringBuilder();

        String header = selectedColumns.stream()
                .map(col -> switch (col) {
                    case "name" -> "Name";
                    case "email" -> "Email";
                    case "phone" -> "Phone Number";
                    case "status" -> "Status";
                    case "address" -> "Address";
                    case "website" -> "Website";
                    case "industry" -> "Industry";
                    case "notes" -> "Notes";
                    case "owner" -> "Company Owner";
                    case "photoUrl" -> "Photo URL";
                    default -> "";
                })
                .collect(Collectors.joining(","));
        csv.append(header).append("\n");

        for (Company company : companies) {
            String row = selectedColumns.stream()
                    .map(col -> switch (col) {
                        case "name" -> escapeCsv(company.getName());
                        case "email" -> escapeCsv(company.getEmail());
                        case "phone" -> escapeCsv(company.getPhone());
                        case "status" -> escapeCsv(company.getStatus());
                        case "address" -> escapeCsv(company.getAddress());
                        case "website" -> escapeCsv(company.getWebsite());
                        case "industry" -> escapeCsv(company.getIndustry());
                        case "notes" -> escapeCsv(company.getNotes());
                        case "owner" -> company.getOwner() != null ? escapeCsv(company.getOwner().getUsername()) : "";
                        case "photoUrl" -> escapeCsv(company.getPhotoUrl());
                        default -> "";
                    })
                    .collect(Collectors.joining(","));
            csv.append(row).append("\n");
        }
        return csv.toString().getBytes();
    }

    public byte[] exportCompaniesToExcel(List<String> columns) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Companies");
            List<Company> companies = companyRepository.findAll();

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                headerRow.createCell(i).setCellValue(columns.get(i));
            }

            int rowNum = 1;
            for (Company company : companies) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i).toLowerCase();
                    Cell cell = row.createCell(i);
                    switch (col) {
                        case "name": cell.setCellValue(company.getName()); break;
                        case "email": cell.setCellValue(company.getEmail()); break;
                        case "phone": cell.setCellValue(company.getPhone()); break;
                        case "status": cell.setCellValue(company.getStatus()); break;
                        case "address": cell.setCellValue(company.getAddress()); break;
                        case "website": cell.setCellValue(company.getWebsite()); break;
                        case "industry": cell.setCellValue(company.getIndustry()); break;
                        case "notes": cell.setCellValue(company.getNotes()); break;
                        case "owner": cell.setCellValue(company.getOwner() != null ? company.getOwner().getUsername() : ""); break;
                        case "photourl": cell.setCellValue(company.getPhotoUrl()); break;
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private String escapeCsv(String value) {
        return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"";
    }
}