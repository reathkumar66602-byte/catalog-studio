package com.catalogstudio.supportticket.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_ticket_messages")
public class SupportTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "author_name", length = 160)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 32)
    private MessageAuthorRole authorRole;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
