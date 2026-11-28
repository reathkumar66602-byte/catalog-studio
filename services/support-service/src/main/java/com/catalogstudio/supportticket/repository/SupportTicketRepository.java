package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportTicket;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.catalogstudio.supportticket.entity.TicketStatus;
import com.catalogstudio.supportticket.entity.TicketPriority;
import java.time.Instant;
import java.util.Collection;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByUuidAndDeletedAtIsNull(UUID uuid);
    Optional<SupportTicket> findByTicketNumberAndDeletedAtIsNull(String ticketNumber);
    boolean existsByTicketNumber(String ticketNumber);

    @Query("""
            select t from SupportTicket t
            where t.deletedAt is null
              and t.customerUserId = :customerId
              and (:status is null or t.status = :status)
              and (:q is null or lower(t.subject) like lower(concat('%', cast(:q as string), '%'))
                   or lower(t.ticketNumber) like lower(concat('%', cast(:q as string), '%'))
                   or lower(t.description) like lower(concat('%', cast(:q as string), '%')))
            """ )
    Page<SupportTicket> searchCustomerTickets(
            @Param("customerId") Long customerId,
            @Param("status") TicketStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            select t from SupportTicket t
            left join t.category c
            where t.deletedAt is null
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:categoryId is null or c.id = :categoryId)
              and (:assigneeId is null or t.assignedAdminUserId = :assigneeId)
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt <= :to)
              and (:slaBreached = false or (t.slaBreachedAt is not null
                   or (t.slaDeadlineAt is not null and t.slaDeadlineAt < :now
                       and t.status not in (com.catalogstudio.supportticket.entity.TicketStatus.RESOLVED,
                                            com.catalogstudio.supportticket.entity.TicketStatus.CLOSED))))
              and (:q is null or lower(t.subject) like lower(concat('%', cast(:q as string), '%'))
                   or lower(t.ticketNumber) like lower(concat('%', cast(:q as string), '%'))
                   or lower(t.customerEmail) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(t.customerName, '')) like lower(concat('%', cast(:q as string), '%')))
            """ )
    Page<SupportTicket> searchAdminTickets(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("categoryId") Long categoryId,
            @Param("assigneeId") Long assigneeId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("slaBreached") boolean slaBreached,
            @Param("now") Instant now,
            @Param("q") String q,
            Pageable pageable);

    long countByDeletedAtIsNull();
    long countByDeletedAtIsNullAndStatus(TicketStatus status);
    long countByDeletedAtIsNullAndAssignedAdminUserIdIsNullAndStatusIn(Collection<TicketStatus> statuses);

    @Query("""
            select count(t) from SupportTicket t
            where t.deletedAt is null
              and t.status not in (com.catalogstudio.supportticket.entity.TicketStatus.RESOLVED,
                                   com.catalogstudio.supportticket.entity.TicketStatus.CLOSED)
              and t.slaDeadlineAt is not null
              and t.slaDeadlineAt < :now
            """)
    long countSlaBreached(@Param("now") Instant now);

    @Query("""
            select t from SupportTicket t
            where t.deletedAt is null
              and t.status not in (com.catalogstudio.supportticket.entity.TicketStatus.RESOLVED,
                                   com.catalogstudio.supportticket.entity.TicketStatus.CLOSED)
              and t.slaDeadlineAt is not null
              and (
                    (t.slaWarnedAt is null and t.slaDeadlineAt <= :warnBefore)
                 or (t.slaBreachedAt is null and t.slaDeadlineAt < :now)
              )
            """)
    List<SupportTicket> findTicketsNeedingSlaAttention(
            @Param("now") Instant now,
            @Param("warnBefore") Instant warnBefore);

}
