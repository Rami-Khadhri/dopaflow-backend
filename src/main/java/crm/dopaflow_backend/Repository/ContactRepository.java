package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

        Page<Contact> findAll(Pageable pageable);

        Page<Contact> findByNameContainingIgnoreCase(String name, Pageable pageable);

        Page<Contact> findByStatusAndOwnerIdAndCreatedAtBetween(
                String status,
                Long ownerId,
                LocalDateTime startDate,
                LocalDateTime endDate,
                Pageable pageable
        );

        Page<Contact> findByStatusAndCreatedAtBetween(
                String status,
                LocalDateTime startDate,
                LocalDateTime endDate,
                Pageable pageable
        );

        Page<Contact> findByStatusAndOwnerIsNullAndCreatedAtBetween(
                String status,
                LocalDateTime startDate,
                LocalDateTime endDate,
                Pageable pageable
        );

        Page<Contact> findByOwnerIsNull(Pageable pageable);

        // New methods without status filter
        Page<Contact> findByOwnerIdAndCreatedAtBetween(
                Long ownerId,
                LocalDateTime startDate,
                LocalDateTime endDate,
                Pageable pageable
        );

        Page<Contact> findByOwnerIsNullAndCreatedAtBetween(
                LocalDateTime startDate,
                LocalDateTime endDate,
                Pageable pageable
        );

        Page<Contact> findByCreatedAtBetween(
                LocalDateTime startDate,
                LocalDateTime endDate,
                Pageable pageable
        );

        List<Contact> findByOwnerId(Long ownerId);
        Contact findContactById(Long aLong);

        Page<Contact> findByCompanyIdAndCreatedAtBetween(Long companyId, LocalDateTime start, LocalDateTime end, Pageable pageable);
        Page<Contact> findByStatusAndCompanyIdAndCreatedAtBetween(String status, Long companyId, LocalDateTime start, LocalDateTime end, Pageable pageable);
        Page<Contact> findByOwnerIdAndCompanyIdAndCreatedAtBetween(Long ownerId, Long companyId, LocalDateTime start, LocalDateTime end, Pageable pageable);

        Page<Contact> findByOwnerIsNullAndCompanyIdAndCreatedAtBetween(Long companyId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

        Page<Contact> findByStatusAndOwnerIsNullAndCompanyIdAndCreatedAtBetween(String filteredStatus, Long companyId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

        Page<Contact> findByStatusAndOwnerIdAndCompanyIdAndCreatedAtBetween(String filteredStatus, Long ownerId, Long companyId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

        List<Contact> findByEmailIn(List<String> emails);
        List<Contact> findByCompanyId(Long companyId);

        // Unassign company from contacts (set company_id to NULL)
        @Modifying
        @Query("UPDATE Contact c SET c.company = NULL WHERE c.company.id = :companyId")
        void unassignCompany(Long companyId);
}