package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.Company;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Model.Contact; // Add Contact model
import crm.dopaflow_backend.Repository.CompanyRepository;
import crm.dopaflow_backend.Repository.UserRepository;
import crm.dopaflow_backend.Repository.ContactRepository; // Add ContactRepository
import crm.dopaflow_backend.Utils.ProgressTracker;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // For transaction management

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository; // Add ContactRepository

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
        return companyRepository.findById(id).orElse(null);
    }

    public Company createCompany(Company company) {
        try {
            if (company.getOwner() != null && company.getOwner().getId() != null) {
                User owner = userRepository.findById(company.getOwner().getId()).orElse(null);
                company.setOwner(owner);
            }
            return companyRepository.save(company);
        } catch (Exception e) {
            return null; // Return null instead of throwing an exception
        }
    }

    public Company updateCompany(Long id, Company companyDetails) {
        try {
            Company existingCompany = companyRepository.findById(id).orElse(null);
            if (existingCompany == null) return null;

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
                User owner = userRepository.findById(companyDetails.getOwner().getId()).orElse(null);
                existingCompany.setOwner(owner);
            } else if (companyDetails.getOwner() == null) {
                existingCompany.setOwner(null);
            }

            return companyRepository.save(existingCompany);
        } catch (Exception e) {
            return null;
        }
    }

    // Method to unassign a company from all related contacts
    @Transactional
    public void unassignCompanyFromContacts(Long companyId) {
        try {
            contactRepository.unassignCompany(companyId);
        } catch (Exception e) {
            // Log the error but don't throw it to ensure deletion can proceed
            System.err.println("Failed to unassign company from contacts: " + e.getMessage());
        }
    }

    // Updated deleteCompany method to unassign contacts first
    @Transactional
    public boolean deleteCompany(Long id) {
        try {
            Company company = companyRepository.findById(id).orElse(null);
            if (company == null) return false;

            // Unassign the company from all related contacts
            unassignCompanyFromContacts(id);

            // Now delete the company
            companyRepository.delete(company);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to delete company: " + e.getMessage());
            return false;
        }
    }

    // New method to handle bulk deletion of companies
    @Transactional
    public boolean deleteCompanies(List<Long> companyIds) {
        try {
            // Fetch all companies to delete
            List<Company> companiesToDelete = companyRepository.findAllById(companyIds);
            if (companiesToDelete.isEmpty()) return false;

            // Unassign each company from related contacts
            for (Company company : companiesToDelete) {
                unassignCompanyFromContacts(company.getId());
            }

            // Delete all companies
            companyRepository.deleteAll(companiesToDelete);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to delete companies: " + e.getMessage());
            return false;
        }
    }

    public ImportResult<Company> bulkImportCompanies(List<Company> importedCompanies, boolean updateExisting, ProgressTracker progressTracker) {
        ImportResult<Company> result = new ImportResult<>();
        List<Company> companiesToSave = new ArrayList<>();
        int total = importedCompanies.size();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int processed = 0;

        // Fetch all existing users and companies once to avoid repeated queries
        Set<String> ownerUsernames = importedCompanies.stream()
                .map(Company::getOwnerUsername)
                .filter(username -> username != null && !username.trim().isEmpty())
                .collect(Collectors.toSet());
        List<User> existingUsers = userRepository.findByUsernameIn(new ArrayList<>(ownerUsernames));
        Map<String, User> userMap = existingUsers.stream()
                .collect(Collectors.toMap(User::getUsername, u -> u, (u1, u2) -> u1));

        List<String> companyNames = importedCompanies.stream()
                .map(Company::getName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toList());
        List<Company> existingCompanies = companyRepository.findByNameIn(companyNames);
        Map<String, Company> existingCompanyMap = existingCompanies.stream()
                .collect(Collectors.toMap(Company::getName, c -> c, (c1, c2) -> c1));

        for (Company imported : importedCompanies) {
            processed++;
            progressTracker.updateProgress((double) processed / total * 100);

            String name = imported.getName();
            if (name == null || name.trim().isEmpty()) {
                skipped++;
                continue;
            }

            Company company;
            if (existingCompanyMap.containsKey(name)) {
                if (updateExisting) {
                    company = existingCompanyMap.get(name);
                    updateCompanyFields(company, imported, userMap);
                    updated++;
                } else {
                    skipped++;
                    continue;
                }
            } else {
                company = createNewCompany(imported, userMap);
                if (company != null) {
                    created++;
                } else {
                    skipped++;
                    continue;
                }
            }
            companiesToSave.add(company);
        }

        try {
            if (!companiesToSave.isEmpty()) {
                companyRepository.saveAll(companiesToSave);
            }
        } catch (Exception e) {
            // Log the error but don't throw it
            skipped += companiesToSave.size();
            created = 0;
            updated = 0;
            companiesToSave.clear();
        }

        result.setSavedEntities(companiesToSave);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        return result;
    }

    private Company createNewCompany(Company imported, Map<String, User> userMap) {
        try {
            Company company = new Company();
            company.setName(imported.getName());
            company.setEmail(imported.getEmail() != null ? imported.getEmail() : "unknown@dopaflow.com");
            company.setPhone(imported.getPhone() != null ? imported.getPhone() : "N/A");
            company.setStatus(imported.getStatus() != null ? imported.getStatus() : "Active");
            company.setAddress(imported.getAddress() != null ? imported.getAddress() : "N/A");
            company.setWebsite(imported.getWebsite() != null ? imported.getWebsite() : "N/A");
            company.setIndustry(imported.getIndustry() != null ? imported.getIndustry() : "N/A");
            company.setNotes(imported.getNotes());
            company.setPhotoUrl(imported.getPhotoUrl());
            if (imported.getOwnerUsername() != null && !imported.getOwnerUsername().trim().isEmpty()) {
                company.setOwner(userMap.get(imported.getOwnerUsername()));
            }
            return company;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateCompanyFields(Company existing, Company imported, Map<String, User> userMap) {
        if (imported.getEmail() != null) existing.setEmail(imported.getEmail());
        if (imported.getPhone() != null) existing.setPhone(imported.getPhone());
        if (imported.getStatus() != null) existing.setStatus(imported.getStatus());
        if (imported.getAddress() != null) existing.setAddress(imported.getAddress());
        if (imported.getWebsite() != null) existing.setWebsite(imported.getWebsite());
        if (imported.getIndustry() != null) existing.setIndustry(imported.getIndustry());
        if (imported.getNotes() != null) existing.setNotes(imported.getNotes());
        if (imported.getPhotoUrl() != null) existing.setPhotoUrl(imported.getPhotoUrl());
        if (imported.getOwnerUsername() != null && !imported.getOwnerUsername().trim().isEmpty()) {
            existing.setOwner(userMap.get(imported.getOwnerUsername()));
        }
    }

    public byte[] exportCompaniesToCsv(List<String> selectedColumns) {
        List<Company> companies = companyRepository.findAll();
        StringBuilder csv = new StringBuilder();

        String header = selectedColumns.stream()
                .map(col -> switch (col.toLowerCase()) {
                    case "name" -> "Name";
                    case "email" -> "Email";
                    case "phone" -> "Phone Number";
                    case "status" -> "Status";
                    case "address" -> "Address";
                    case "website" -> "Website";
                    case "industry" -> "Industry";
                    case "notes" -> "Notes";
                    case "owner" -> "Company Owner";
                    case "photourl" -> "Photo URL";
                    default -> col;
                })
                .collect(Collectors.joining(","));
        csv.append(header).append("\n");

        for (Company company : companies) {
            String row = selectedColumns.stream()
                    .map(col -> switch (col.toLowerCase()) {
                        case "name" -> escapeCsv(company.getName());
                        case "email" -> escapeCsv(company.getEmail());
                        case "phone" -> escapeCsv(company.getPhone());
                        case "status" -> escapeCsv(company.getStatus());
                        case "address" -> escapeCsv(company.getAddress());
                        case "website" -> escapeCsv(company.getWebsite());
                        case "industry" -> escapeCsv(company.getIndustry());
                        case "notes" -> escapeCsv(company.getNotes());
                        case "owner" -> company.getOwner() != null ? escapeCsv(company.getOwner().getUsername()) : "";
                        case "photourl" -> escapeCsv(company.getPhotoUrl());
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