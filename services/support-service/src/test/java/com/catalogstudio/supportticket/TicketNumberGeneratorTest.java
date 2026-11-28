package com.catalogstudio.supportticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import com.catalogstudio.supportticket.service.TicketNumberGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketNumberGeneratorTest {

    @Mock
    SupportTicketRepository ticketRepository;

    @Test
    void generatesUniqueCsDaySequenceNumbers() {
        when(ticketRepository.existsByTicketNumber(anyString())).thenReturn(false);

        TicketNumberGenerator generator = new TicketNumberGenerator(ticketRepository);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String number = generator.next();
            assertThat(number).matches("CS-\\d{8}-\\d{5}");
            assertThat(seen.add(number)).isTrue();
        }
    }

    @Test
    void retriesWhenCollisionOccurs() {
        when(ticketRepository.existsByTicketNumber(anyString()))
                .thenReturn(true, true, false);

        TicketNumberGenerator generator = new TicketNumberGenerator(ticketRepository);
        String number = generator.next();
        assertThat(number).matches("CS-\\d{8}-\\d{5}");
    }
}
