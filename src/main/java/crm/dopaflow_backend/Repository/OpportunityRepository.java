package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.Opportunity;
import crm.dopaflow_backend.Model.StatutOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    @Query("SELECT COUNT(o) FROM Opportunity o WHERE o.status = :status")
    long countOpenOpportunities(@Param("status") StatutOpportunity status);

    @Query("SELECT SUM(o.value) FROM Opportunity o WHERE o.status = :status")
    Double getTotalOpportunityValue(@Param("status") StatutOpportunity status);

    @Query("SELECT o FROM Opportunity o WHERE o.status = :status AND o.createdAt >= :startDate")
    List<Opportunity> findWonOpportunitiesSince(@Param("status") StatutOpportunity status, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT o FROM Opportunity o WHERE o.status = :status AND o.owner.id = :userId AND o.createdAt >= :startDate")
    List<Opportunity> findWonOpportunitiesSinceForUser(
            @Param("status") StatutOpportunity status,
            @Param("startDate") LocalDateTime startDate,
            @Param("userId") Long userId);

    List<Opportunity> findTop3ByOrderByValueDesc();
    @Query("SELECT COUNT(o) FROM Opportunity o WHERE o.createdAt >= :startDate AND o.status = :status")
    long countByCreatedAtAfterAndStatus(@Param("startDate") LocalDateTime startDate, @Param("status") StatutOpportunity status);
}