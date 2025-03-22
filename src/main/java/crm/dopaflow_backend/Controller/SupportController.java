package crm.dopaflow_backend.Controller;

import crm.dopaflow_backend.DTO.MessageDTO;
import crm.dopaflow_backend.DTO.TicketDTO;
import crm.dopaflow_backend.DTO.TicketMessageDTO;
import crm.dopaflow_backend.Model.SupportTicket;
import crm.dopaflow_backend.Model.TicketStatus;
import crm.dopaflow_backend.Service.SupportTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    @Autowired
    private SupportTicketService supportTicketService;

    @PostMapping("/tickets")
    public ResponseEntity<TicketDTO> createTicket(@RequestBody TicketDTO ticketDTO) {
        SupportTicket ticket = supportTicketService.createTicket(ticketDTO);
        TicketDTO createdTicketDTO = supportTicketService.getTicketSummaryById(ticket.getId());
        return new ResponseEntity<>(createdTicketDTO, HttpStatus.CREATED);
    }

    @PostMapping("/messages")
    public ResponseEntity<TicketMessageDTO> addMessage(
            @RequestPart("message") MessageDTO messageDTO,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws Exception {
        TicketMessageDTO message = supportTicketService.addMessage(messageDTO, files);
        return new ResponseEntity<>(message, HttpStatus.CREATED);
    }

    @PutMapping("/tickets/{id}/status")
    public ResponseEntity<Void> setTicketStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        TicketStatus status = TicketStatus.valueOf(payload.get("status").toUpperCase());
        supportTicketService.setTicketStatus(id, status);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/tickets/{id}/read")
    public ResponseEntity<Void> markMessagesAsRead(@PathVariable Long id) {
        supportTicketService.markMessagesAsRead(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<TicketDTO>> getUserTickets() {
        List<TicketDTO> tickets = supportTicketService.getUserTicketSummaries();
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }

    @GetMapping("/tickets/all")
    @PreAuthorize("hasRole('SuperAdmin')")
    public ResponseEntity<List<TicketDTO>> getAllTickets() {
        List<TicketDTO> tickets = supportTicketService.getAllTicketSummaries();
        return new ResponseEntity<>(tickets, HttpStatus.OK);
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketDTO> getTicketById(@PathVariable Long id) {
        TicketDTO ticket = supportTicketService.getTicketById(id);
        return new ResponseEntity<>(ticket, HttpStatus.OK);
    }

    @GetMapping("/tickets/{id}/messages")
    public ResponseEntity<List<TicketMessageDTO>> getTicketMessages(@PathVariable Long id) {
        List<TicketMessageDTO> messages = supportTicketService.getTicketMessages(id);
        return new ResponseEntity<>(messages, HttpStatus.OK);
    }

    @DeleteMapping("/tickets/{id}")
    @PreAuthorize("hasRole('SuperAdmin')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        supportTicketService.deleteTicket(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}