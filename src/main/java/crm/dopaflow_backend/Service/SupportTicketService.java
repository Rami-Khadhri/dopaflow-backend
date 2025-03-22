package crm.dopaflow_backend.Service;

import crm.dopaflow_backend.DTO.MessageDTO;
import crm.dopaflow_backend.DTO.TicketDTO;
import crm.dopaflow_backend.DTO.TicketMessageDTO;
import crm.dopaflow_backend.DTO.UserDTO;
import crm.dopaflow_backend.Model.*;
import crm.dopaflow_backend.Repository.SupportTicketRepository;
import crm.dopaflow_backend.Repository.TicketMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportTicketService {
    private static final Logger logger = LoggerFactory.getLogger(SupportTicketService.class);
    private final SupportTicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final UserService userService;
    private final AttachmentUploadService attachmentUploadService;
    private final NotificationService notificationService;

    @Transactional
    public SupportTicket createTicket(TicketDTO ticketDTO) {
        if (ticketDTO.getSubject() == null || ticketDTO.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        if (ticketDTO.getAssignee() == null || ticketDTO.getAssignee().getEmail() == null || ticketDTO.getAssignee().getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Assignee email cannot be null or empty");
        }

        String creatorEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User creator = userService.findByEmail(creatorEmail)
                .orElseThrow(() -> new IllegalArgumentException("Creator not found: " + creatorEmail));
        User assignee = userService.findByEmail(ticketDTO.getAssignee().getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found: " + ticketDTO.getAssignee().getEmail()));

        SupportTicket ticket = new SupportTicket();
        ticket.setSubject(ticketDTO.getSubject().trim());
        ticket.setContent(ticketDTO.getContent() != null ? ticketDTO.getContent().trim() : "");
        ticket.setCreator(creator);
        ticket.setAssignee(assignee);
        ticket.setStatus(TicketStatus.OPENED);

        SupportTicket savedTicket = ticketRepository.save(ticket);
        logger.info("Ticket created: ID={}, Subject={}, Creator={}, Assignee={}",
                savedTicket.getId(), savedTicket.getSubject(), creatorEmail, assignee.getEmail());

        notificationService.createNotification(
                assignee,
                "New ticket assigned: " + savedTicket.getSubject(),
                "/tickets/" + savedTicket.getId(),
                Notification.NotificationType.TICKET_OPENED
        );

        return savedTicket;
    }

    @Transactional
    public TicketMessageDTO addMessage(MessageDTO messageDTO, List<MultipartFile> files) throws IOException {
        SupportTicket ticket = ticketRepository.findById(messageDTO.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + messageDTO.getTicketId()));

        if (ticket.getStatus() == TicketStatus.CLOSED || ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new IllegalStateException("Cannot add message to a closed or resolved ticket");
        }

        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User sender = userService.findByEmail(authEmail)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + authEmail));

        if (!sender.getId().equals(ticket.getCreator().getId()) && !sender.getId().equals(ticket.getAssignee().getId())) {
            logger.error("Authenticated user {} (ID={}) is neither creator (ID={}) nor assignee (ID={}) of ticket ID={}",
                    authEmail, sender.getId(), ticket.getCreator().getId(), ticket.getAssignee().getId(), ticket.getId());
            throw new IllegalStateException("Only the ticket creator or assignee can send messages");
        }

        TicketMessage message = new TicketMessage();
        String content = messageDTO.getContent() != null ? messageDTO.getContent().trim() : "";
        message.setTicket(ticket);
        message.setSender(sender);
        message.setRead(false);

        if (files != null && !files.isEmpty()) {
            List<String> attachmentUrls = attachmentUploadService.uploadAttachments(files);
            message.setAttachments(attachmentUrls);
            if (content.isEmpty()) content = "";
        }
        message.setContent(content);

        TicketMessage savedMessage = messageRepository.save(message);

        if (ticket.getStatus() == TicketStatus.OPENED) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            logger.info("Ticket updated to IN_PROGRESS: ID={}", ticket.getId());
        }

        logger.info("Message added: ID={}, TicketID={}, Sender={}", savedMessage.getId(), ticket.getId(), authEmail);

        User recipient = ticket.getCreator().getId().equals(sender.getId()) ? ticket.getAssignee() : ticket.getCreator();
        notificationService.createNotification(
                recipient,
                "New message in: " + ticket.getSubject(),
                "/tickets/" + ticket.getId(),
                Notification.NotificationType.MESSAGE_RECEIVED
        );

        return mapMessageToDTO(savedMessage);
    }

    @Transactional
    public void markMessagesAsRead(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User authUser = userService.findByEmail(authEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authEmail));

        ticket.getMessages().stream()
                .filter(msg -> !msg.isRead() && !msg.getSender().getId().equals(authUser.getId()))
                .forEach(msg -> {
                    msg.setRead(true);
                    messageRepository.save(msg);
                });
        logger.info("Marked messages as read for ticket ID={}", ticketId);
    }

    @Transactional
    public void setTicketStatus(Long ticketId, TicketStatus newStatus) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User authUser = userService.findByEmail(authEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authEmail));

        if (newStatus == TicketStatus.CLOSED) {
            if (!authUser.getRole().equals(Role.SuperAdmin) && !ticket.getCreator().getId().equals(authUser.getId())) {
                throw new IllegalStateException("Only creator or superadmin can close the ticket");
            }
        } else if (newStatus == TicketStatus.RESOLVED) {
            if (!authUser.getRole().equals(Role.SuperAdmin) && !ticket.getAssignee().getId().equals(authUser.getId())) {
                throw new IllegalStateException("Only assignee or superadmin can resolve the ticket");
            }
        } else {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        ticket.setStatus(newStatus);
        ticketRepository.save(ticket);
        logger.info("Ticket status set to {}: ID={}", newStatus, ticketId);

        notificationService.createNotification(
                ticket.getCreator(),
                "Ticket " + newStatus.toString().toLowerCase() + ": " + ticket.getSubject(),
                "/tickets/" + ticketId,
                Notification.NotificationType.TICKET_STATUS_CHANGED
        );
        notificationService.createNotification(
                ticket.getAssignee(),
                "Ticket " + newStatus.toString().toLowerCase() + ": " + ticket.getSubject(),
                "/tickets/" + ticketId,
                Notification.NotificationType.TICKET_STATUS_CHANGED
        );
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void closeInactiveTickets() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<SupportTicket> inactiveTickets = ticketRepository.findInactiveTickets(TicketStatus.OPENED, sevenDaysAgo);
        for (SupportTicket ticket : inactiveTickets) {
            ticket.setStatus(TicketStatus.CLOSED);
            ticketRepository.save(ticket);
            logger.info("Auto-closed inactive ticket: ID={}", ticket.getId());
            notificationService.createNotification(
                    ticket.getCreator(),
                    "Ticket auto-closed due to inactivity: " + ticket.getSubject(),
                    "/tickets/" + ticket.getId(),
                    Notification.NotificationType.TICKET_CLOSED
            );
        }
    }

    public List<TicketDTO> getUserTicketSummaries() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        List<SupportTicket> tickets = ticketRepository.findByCreatorIdOrAssigneeId(user.getId(), user.getId());
        return mapToSummaryDTOs(tickets);
    }

    public List<TicketDTO> getAllTicketSummaries() {
        List<SupportTicket> tickets = ticketRepository.findAll();
        return mapToSummaryDTOs(tickets);
    }

    public TicketDTO getTicketById(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        return mapToDTO(ticket);
    }

    public TicketDTO getTicketSummaryById(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        return mapToSummaryDTO(ticket);
    }

    public List<TicketMessageDTO> getTicketMessages(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User authUser = userService.findByEmail(authEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authEmail));

        if (!authUser.getRole().equals(Role.SuperAdmin) &&
                !ticket.getCreator().getId().equals(authUser.getId()) &&
                !ticket.getAssignee().getId().equals(authUser.getId())) {
            throw new IllegalStateException("User does not have permission to view this ticket's messages");
        }

        return ticket.getMessages().stream()
                .map(this::mapMessageToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTicket(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User authUser = userService.findByEmail(authEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authEmail));
        if (!authUser.getRole().equals(Role.SuperAdmin)) {
            throw new IllegalStateException("Only superadmins can delete tickets");
        }
        ticketRepository.delete(ticket);
        logger.info("Ticket deleted: ID={}", ticketId);
    }

    private List<TicketDTO> mapToSummaryDTOs(List<SupportTicket> tickets) {
        return tickets.stream()
                .sorted(Comparator.comparing(this::getLatestMessageTimestamp, Comparator.reverseOrder()))
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());
    }

    private LocalDateTime getLatestMessageTimestamp(SupportTicket ticket) {
        return ticket.getMessages() != null && !ticket.getMessages().isEmpty()
                ? ticket.getMessages().stream()
                .map(TicketMessage::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(ticket.getCreatedAt())
                : ticket.getCreatedAt();
    }

    private TicketDTO mapToSummaryDTO(SupportTicket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setSubject(ticket.getSubject());
        dto.setContent(ticket.getContent());
        dto.setStatus(ticket.getStatus());
        dto.setCreatedAt(ticket.getCreatedAt());

        UserDTO creatorDTO = new UserDTO();
        creatorDTO.setId(ticket.getCreator().getId());
        creatorDTO.setEmail(ticket.getCreator().getEmail());
        creatorDTO.setUsername(ticket.getCreator().getUsername());
        creatorDTO.setProfilePhotoUrl(ticket.getCreator().getProfilePhotoUrl());
        dto.setCreator(creatorDTO);

        UserDTO assigneeDTO = new UserDTO();
        assigneeDTO.setId(ticket.getAssignee().getId());
        assigneeDTO.setEmail(ticket.getAssignee().getEmail());
        assigneeDTO.setUsername(ticket.getAssignee().getUsername());
        assigneeDTO.setProfilePhotoUrl(ticket.getAssignee().getProfilePhotoUrl());
        dto.setAssignee(assigneeDTO);

        // Exclude messages for summary, calculate unreadCount
        dto.setMessages(Collections.emptyList());
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        dto.setUnreadCount(ticket.getMessages() != null
                ? ticket.getMessages().stream()
                .filter(msg -> !msg.isRead() && !msg.getSender().getEmail().equals(authEmail))
                .count()
                : 0L);

        return dto;
    }

    private TicketDTO mapToDTO(SupportTicket ticket) {
        TicketDTO dto = mapToSummaryDTO(ticket);
        List<TicketMessageDTO> messageDTOs = ticket.getMessages() != null
                ? ticket.getMessages().stream().map(this::mapMessageToDTO).collect(Collectors.toList())
                : Collections.emptyList();
        dto.setMessages(messageDTOs);
        return dto;
    }

    private TicketMessageDTO mapMessageToDTO(TicketMessage message) {
        TicketMessageDTO msgDTO = new TicketMessageDTO();
        msgDTO.setId(message.getId());
        msgDTO.setContent(message.getContent());
        msgDTO.setTimestamp(message.getTimestamp());
        msgDTO.setAttachments(message.getAttachments());
        msgDTO.setRead(message.isRead());

        UserDTO senderDTO = new UserDTO();
        senderDTO.setId(message.getSender().getId());
        senderDTO.setEmail(message.getSender().getEmail());
        senderDTO.setUsername(message.getSender().getUsername());
        senderDTO.setProfilePhotoUrl(message.getSender().getProfilePhotoUrl());
        msgDTO.setSender(senderDTO);

        return msgDTO;
    }
}