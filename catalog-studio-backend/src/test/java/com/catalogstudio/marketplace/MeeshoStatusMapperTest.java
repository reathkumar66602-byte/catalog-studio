package com.catalogstudio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.marketplace.oms.MeeshoStatusMapper;
import org.junit.jupiter.api.Test;

class MeeshoStatusMapperTest {

    private final MeeshoStatusMapper mapper = new MeeshoStatusMapper();

    @Test
    void mapsChannelStatusesToInternal() {
        assertThat(mapper.toInternal("Pending")).isEqualTo("NEW");
        assertThat(mapper.toInternal("Ready to ship")).isEqualTo("PACKED");
        assertThat(mapper.toInternal("Dispatched")).isEqualTo("SHIPPED");
        assertThat(mapper.toInternal("Cancelled")).isEqualTo("CANCELLED");
        assertThat(mapper.toInternal("Return IN_TRANSIT")).isEqualTo("RETURN");
    }

    @Test
    void mapsInternalStatusesToMeesho() {
        assertThat(mapper.toMeesho("PACKED")).isEqualTo("Ready to ship");
        assertThat(mapper.toMeesho("SHIPPED")).isEqualTo("Dispatched");
        assertThat(mapper.toMeesho("PROCESSING")).isEqualTo("Accepted");
    }
}
