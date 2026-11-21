package com.example.aitask.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class AiPropertiesTest {
    @Test
    void rejectsNegativeResultLimitsAndAcceptsZero() {
        assertThatThrownBy(() -> new AiProperties(null, DataSize.ofBytes(-1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-result-bytes");
        assertThat(new AiProperties(null, DataSize.ofBytes(0), null).maxResultBytes().toBytes()).isZero();
    }

    @Test
    void rejectsNonpositiveRequestTimeouts() {
        for (Duration timeout : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThatThrownBy(() -> new AiProperties(timeout, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("request-timeout must be positive");
        }
    }

    @Test
    void preservesDefaultAndExplicitPositiveTimeouts() {
        assertThat(new AiProperties(null, null, null).requestTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(new AiProperties(Duration.ofSeconds(1), null, null).requestTimeout())
                .isEqualTo(Duration.ofSeconds(1));
    }
}
