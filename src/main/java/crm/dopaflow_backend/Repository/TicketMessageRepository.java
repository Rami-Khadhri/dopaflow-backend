package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
}