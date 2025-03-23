package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    List<TicketMessage> findBySenderId(Long senderId);
}