package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Page<Company> findAll(Pageable pageable);

    Page<Company> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Company> findByStatusAndOwnerId(
            String status,
            Long ownerId,
            Pageable pageable
    );

    Page<Company> findByStatus(
            String status,
            Pageable pageable
    );

    Page<Company> findByStatusAndOwnerIsNull(
            String status,
            Pageable pageable
    );

    Page<Company> findByOwnerId(
            Long ownerId,
            Pageable pageable
    );

    Page<Company> findByOwnerIsNull(
            Pageable pageable
    );

    Optional<Company> findByName(String name);
}