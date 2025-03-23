package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.Model.Company;
import crm.dopaflow_backend.Model.Contact;
import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Repository.CompanyRepository;
import crm.dopaflow_backend.Repository.ContactRepository;
import crm.dopaflow_backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

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

    public Page<Contact> filterContacts(String status, String startDateStr, String endDateStr, Long ownerId, boolean unassignedOnly, Long companyId, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        LocalDateTime startDate = parseDate(startDateStr, true);
        LocalDateTime endDate = parseDate(endDateStr, false);
        String filteredStatus = (status != null && !status.trim().isEmpty()) ? status : "ANY";

        System.out.println("Filtering contacts: status=" + filteredStatus + ", startDate=" + startDate + ", endDate=" + endDate +
                ", ownerId=" + ownerId + ", unassignedOnly=" + unassignedOnly + ", companyId=" + companyId + ", page=" + page + ", size=" + size + ", sort=" + sort);

        Page<Contact> result;
        if ("ANY".equals(filteredStatus)) {
            if (unassignedOnly) {
                if (companyId != null) {
                    result = contactRepository.findByOwnerIsNullAndCompanyIdAndCreatedAtBetween(companyId, startDate, endDate, pageable);
                } else {
                    result = contactRepository.findByOwnerIsNullAndCreatedAtBetween(startDate, endDate, pageable);
                }
            } else if (ownerId != null) {
                if (companyId != null) {
                    result = contactRepository.findByOwnerIdAndCompanyIdAndCreatedAtBetween(ownerId, companyId, startDate, endDate, pageable);
                } else {
                    result = contactRepository.findByOwnerIdAndCreatedAtBetween(ownerId, startDate, endDate, pageable);
                }
            } else if (companyId != null) {
                result = contactRepository.findByCompanyIdAndCreatedAtBetween(companyId, startDate, endDate, pageable);
            } else {
                result = contactRepository.findByCreatedAtBetween(startDate, endDate, pageable);
            }
        } else {
            if (unassignedOnly) {
                if (companyId != null) {
                    result = contactRepository.findByStatusAndOwnerIsNullAndCompanyIdAndCreatedAtBetween(filteredStatus, companyId, startDate, endDate, pageable);
                } else {
                    result = contactRepository.findByStatusAndOwnerIsNullAndCreatedAtBetween(filteredStatus, startDate, endDate, pageable);
                }
            } else if (ownerId != null) {
                if (companyId != null) {
                    result = contactRepository.findByStatusAndOwnerIdAndCompanyIdAndCreatedAtBetween(filteredStatus, ownerId, companyId, startDate, endDate, pageable);
                } else {
                    result = contactRepository.findByStatusAndOwnerIdAndCreatedAtBetween(filteredStatus, ownerId, startDate, endDate, pageable);
                }
            } else if (companyId != null) {
                result = contactRepository.findByStatusAndCompanyIdAndCreatedAtBetween(filteredStatus, companyId, startDate, endDate, pageable);
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
        if (contact.getCompany() != null) {
            if (contact.getCompany().getId() != null) {
                Company company = companyRepository.findById(contact.getCompany().getId())
                        .orElseThrow(() -> new RuntimeException("Company not found"));
                contact.setCompany(company);
            } else if (contact.getCompany().getName() != null && !contact.getCompany().getName().trim().isEmpty()) {
                Company company = companyService.createCompany(contact.getCompany());
                contact.setCompany(company);
            } else {
                contact.setCompany(null);
            }
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

        if (contactDetails.getCompany() != null) {
            if (contactDetails.getCompany().getId() != null) {
                Company company = companyRepository.findById(contactDetails.getCompany().getId())
                        .orElseThrow(() -> new RuntimeException("Company not found"));
                existingContact.setCompany(company);
            } else if (contactDetails.getCompany().getName() != null && !contactDetails.getCompany().getName().trim().isEmpty()) {
                Company company = companyService.createCompany(contactDetails.getCompany());
                existingContact.setCompany(company);
            } else {
                existingContact.setCompany(null);
            }
        } else {
            existingContact.setCompany(null);
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
                        case "company" -> contact.getCompany() != null ? escapeCsv(contact.getCompany().getName()) : "";
                        case "notes" -> escapeCsv(contact.getNotes());
                        case "photoUrl" -> escapeCsv(contact.getPhotoUrl());
                        default -> "";
                    })
                    .collect(Collectors.joining(","));
            csv.append(row).append("\n");
        }
        return csv.toString().getBytes();
    }

    public byte[] exportContactsToExcel(List<String> columns) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Contacts");
            List<Contact> contacts = contactRepository.findAll();

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                headerRow.createCell(i).setCellValue(columns.get(i));
            }

            int rowNum = 1;
            for (Contact contact : contacts) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i).toLowerCase();
                    Cell cell = row.createCell(i);
                    switch (col) {
                        case "name": cell.setCellValue(contact.getName()); break;
                        case "email": cell.setCellValue(contact.getEmail()); break;
                        case "phone": cell.setCellValue(contact.getPhone()); break;
                        case "status": cell.setCellValue(contact.getStatus() != null ? contact.getStatus() : "Open"); break;
                        case "company": cell.setCellValue(contact.getCompany() != null ? contact.getCompany().getName() : ""); break;
                        case "notes": cell.setCellValue(contact.getNotes()); break;
                        case "owner": cell.setCellValue(contact.getOwner() != null ? contact.getOwner().getUsername() : ""); break;
                        case "createdat": cell.setCellValue(contact.getCreatedAt() != null ? contact.getCreatedAt().toString() : ""); break;
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    public ImportResult<Contact> bulkImportContacts(List<Contact> importedContacts, boolean updateExisting) {
        ImportResult<Contact> result = new ImportResult<>();
        List<Contact> contactsToSave = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        // Extract company names for mapping
        Set<String> companyNames = importedContacts.stream()
                .map(Contact::getCompany)
                .filter(Objects::nonNull)
                .map(Company::getName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toSet());

        // Fetch existing companies by name
        List<Company> existingCompanies = companyRepository.findByNameIn(new ArrayList<>(companyNames));
        Map<String, Company> companyMap = existingCompanies.stream()
                .collect(Collectors.toMap(Company::getName, c -> c, (c1, c2) -> c1)); // Keep first if duplicates

        // Handle company owners
        Set<String> companyOwnerUsernames = importedContacts.stream()
                .map(Contact::getCompany)
                .filter(Objects::nonNull)
                .map(Company::getOwnerUsername)
                .filter(username -> username != null && !username.trim().isEmpty())
                .collect(Collectors.toSet());
        List<User> companyOwners = userRepository.findByUsernameIn(new ArrayList<>(companyOwnerUsernames));
        Map<String, User> companyOwnerMap = companyOwners.stream()
                .collect(Collectors.toMap(User::getUsername, u -> u, (u1, u2) -> u1));

        // Create new companies if they don't exist, using provided values or defaults
        List<Company> newCompanies = importedContacts.stream()
                .map(Contact::getCompany)
                .filter(Objects::nonNull)
                .filter(company -> company.getName() != null && !company.getName().trim().isEmpty())
                .filter(company -> !companyMap.containsKey(company.getName()))
                .map(company -> {
                    Company newCompany = new Company();
                    newCompany.setName(company.getName());
                    newCompany.setEmail(company.getEmail() != null && !company.getEmail().trim().isEmpty() ?
                            company.getEmail() : "unknown_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
                    newCompany.setPhone(company.getPhone() != null && !company.getPhone().trim().isEmpty() ?
                            company.getPhone() : "N/A");
                    newCompany.setStatus(company.getStatus() != null && !company.getStatus().trim().isEmpty() ?
                            company.getStatus() : "Active");
                    newCompany.setAddress(company.getAddress() != null && !company.getAddress().trim().isEmpty() ?
                            company.getAddress() : "N/A");
                    newCompany.setWebsite(company.getWebsite() != null && !company.getWebsite().trim().isEmpty() ?
                            company.getWebsite() : "N/A");
                    newCompany.setIndustry(company.getIndustry() != null && !company.getIndustry().trim().isEmpty() ?
                            company.getIndustry() : "Unknown");
                    newCompany.setNotes(company.getNotes());
                    if (company.getOwnerUsername() != null && !company.getOwnerUsername().trim().isEmpty() &&
                            companyOwnerMap.containsKey(company.getOwnerUsername())) {
                        newCompany.setOwner(companyOwnerMap.get(company.getOwnerUsername()));
                    }
                    newCompany.setOwnerUsername(company.getOwnerUsername());
                    newCompany.setPhotoUrl(company.getPhotoUrl());
                    return newCompany;
                })
                .distinct() // Avoid creating duplicates for the same company name
                .collect(Collectors.toList());

        if (!newCompanies.isEmpty()) {
            companyRepository.saveAll(newCompanies);
            newCompanies.forEach(company -> companyMap.put(company.getName(), company));
        }

        // Extract contact owner usernames
        Set<String> ownerUsernames = importedContacts.stream()
                .map(Contact::getOwnerUsername)
                .filter(username -> username != null && !username.trim().isEmpty())
                .collect(Collectors.toSet());

        // Fetch existing users
        List<User> existingUsers = userRepository.findByUsernameIn(new ArrayList<>(ownerUsernames));
        Map<String, User> userMap = existingUsers.stream()
                .collect(Collectors.toMap(User::getUsername, u -> u, (u1, u2) -> u1));

        // Extract emails to check for duplicates
        List<String> emails = importedContacts.stream()
                .map(Contact::getEmail)
                .filter(email -> email != null && !email.trim().isEmpty())
                .collect(Collectors.toList());

        // Fetch existing contacts
        List<Contact> existingContacts = contactRepository.findByEmailIn(emails);
        Map<String, Contact> existingContactMap = existingContacts.stream()
                .collect(Collectors.toMap(Contact::getEmail, c -> c, (c1, c2) -> c1));

        for (Contact imported : importedContacts) {
            String email = imported.getEmail();
            if (email == null || email.trim().isEmpty()) {
                skipped++; // Skip contacts without email
                continue;
            }

            Contact contact;
            if (existingContactMap.containsKey(email)) {
                if (updateExisting) {
                    contact = existingContactMap.get(email);
                    // Update non-null fields only
                    if (imported.getName() != null) contact.setName(imported.getName());
                    if (imported.getPhone() != null) contact.setPhone(imported.getPhone());
                    if (imported.getStatus() != null) contact.setStatus(imported.getStatus());
                    if (imported.getNotes() != null) contact.setNotes(imported.getNotes());
                    if (imported.getPhotoUrl() != null) contact.setPhotoUrl(imported.getPhotoUrl());
                    // Update company if provided
                    if (imported.getCompany() != null && imported.getCompany().getName() != null && !imported.getCompany().getName().trim().isEmpty()) {
                        contact.setCompany(companyMap.get(imported.getCompany().getName()));
                    }
                    // Update owner if provided
                    if (imported.getOwnerUsername() != null && !imported.getOwnerUsername().trim().isEmpty()) {
                        contact.setOwner(userMap.get(imported.getOwnerUsername()));
                    }
                    contact.setLastActivity(LocalDateTime.now());
                    updated++;
                } else {
                    skipped++; // Skip duplicates if not updating
                    continue;
                }
            } else {
                contact = new Contact();
                contact.setEmail(email);
                contact.setName(imported.getName() != null ? imported.getName() : "Unknown");
                contact.setPhone(imported.getPhone());
                contact.setStatus(imported.getStatus() != null ? imported.getStatus() : "Open");
                contact.setNotes(imported.getNotes());
                contact.setPhotoUrl(imported.getPhotoUrl());
                // Set company if provided
                if (imported.getCompany() != null && imported.getCompany().getName() != null && !imported.getCompany().getName().trim().isEmpty()) {
                    contact.setCompany(companyMap.get(imported.getCompany().getName()));
                }
                // Set owner if provided
                if (imported.getOwnerUsername() != null && !imported.getOwnerUsername().trim().isEmpty()) {
                    contact.setOwner(userMap.get(imported.getOwnerUsername()));
                }
                contact.setCreatedAt(LocalDateTime.now());
                contact.setLastActivity(LocalDateTime.now());
                created++;
            }
            contactsToSave.add(contact);
        }

        if (!contactsToSave.isEmpty()) {
            contactRepository.saveAll(contactsToSave);
        }
        result.setSavedEntities(contactsToSave);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setSkipped(skipped);
        return result;
    }

    public void unassignContactsFromUser(Long userId) {
        List<Contact> contacts = contactRepository.findByOwnerId(userId);
        if (!contacts.isEmpty()) {
            for (Contact contact : contacts) {
                contact.setOwner(null);
            }
            contactRepository.saveAll(contacts);
        }
    }


    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    private String escapeCsv(String value) {
        return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"";
    }
}