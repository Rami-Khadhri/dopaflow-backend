package crm.dopaflow_backend.Model;

public enum TicketStatus {
    OPENED,      // Newly created, no messages yet
    IN_PROGRESS, // Has messages, being worked on
    RESOLVED,    // Issue solved, no further replies
    CLOSED       // Closed manually or auto-closed, no further replies
}