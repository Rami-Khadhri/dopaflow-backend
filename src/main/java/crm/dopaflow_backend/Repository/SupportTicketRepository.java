package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.SupportTicket;
import crm.dopaflow_backend.Model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByCreatorIdOrAssigneeId(Long creatorId, Long assigneeId);

    @Query("SELECT t FROM SupportTicket t WHERE t.status = :status AND t.createdAt < :date AND SIZE(t.messages) = 0")
    List<SupportTicket> findInactiveTickets(@Param("status") TicketStatus status, @Param("date") LocalDateTime date);
}