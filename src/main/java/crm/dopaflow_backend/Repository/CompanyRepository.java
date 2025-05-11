package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findTop50ByOrderByNameAsc();

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
    Page<Company> findByOwnerIsNotNull(
            Pageable pageable
    );


    Optional<Company> findByName(String name);

    List<Company> findByNameIn(List<String> names);

    @Query("SELECT COUNT(c) FROM Company c WHERE c.createdAt >= :startDate")
    long countNewCompaniesSince(@Param("startDate") LocalDateTime startDate);
}