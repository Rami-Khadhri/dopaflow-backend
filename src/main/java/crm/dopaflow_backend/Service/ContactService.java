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
import java.util.List;
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

    public List<Contact> bulkCreateContacts(List<Contact> contacts) {
        return contactRepository.saveAll(contacts.stream().map(contact -> {
            if (contact.getName() == null || contact.getName().trim().isEmpty()) {
                contact.setName("Unknown");
            }
            if (contact.getEmail() == null || contact.getEmail().trim().isEmpty()) {
                contact.setEmail("unknown_" + System.nanoTime() + "@example.com");
            }
            if (contact.getPhone() == null || contact.getPhone().trim().isEmpty()) {
                contact.setPhone(null);
            }
            if (contact.getStatus() == null || contact.getStatus().trim().isEmpty()) {
                contact.setStatus("Open");
            }
            if (contact.getCompany() != null) {
                if (contact.getCompany().getId() != null) {
                    Company company = companyRepository.findById(contact.getCompany().getId())
                            .orElseThrow(() -> new RuntimeException("Company not found: " + contact.getCompany().getId()));
                    contact.setCompany(company);
                } else if (contact.getCompany().getName() != null && !contact.getCompany().getName().trim().isEmpty()) {
                    // Use CompanyService to create a valid company
                    Company company = companyService.createCompany(contact.getCompany());
                    contact.setCompany(company);
                } else {
                    contact.setCompany(null);
                }
            } else {
                contact.setCompany(null);
            }
            if (contact.getNotes() == null || contact.getNotes().trim().isEmpty()) {
                contact.setNotes(null);
            }

            contact.setCreatedAt(LocalDateTime.now());
            contact.setLastActivity(LocalDateTime.now());

            if (contact.getOwnerUsername() != null && !contact.getOwnerUsername().trim().isEmpty()) {
                User owner = userRepository.findByUsername(contact.getOwnerUsername()).orElse(null);
                contact.setOwner(owner);
            } else if (contact.getOwner() != null && contact.getOwner().getId() != null) {
                User owner = userRepository.findById(contact.getOwner().getId())
                        .orElseThrow(() -> new RuntimeException("Owner not found: " + contact.getOwner().getId()));
                contact.setOwner(owner);
            }
            contact.setOwnerUsername(null);

            return contact;
        }).collect(Collectors.toList()));
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