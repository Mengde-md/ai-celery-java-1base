package com.example.aitask.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogSanitizerTest {
    @ParameterizedTest
    @ValueSource(strings = {"password", "apiKey", "api_key", "authorization", "accessToken", "refreshToken"})
    void masksTheEntireSensitiveValueIncludingEscapedQuotes(String field) {
        String value = "{\"" + field + "\":\"prefix\\\"private-suffix\\\\end\",\"message\":\"public\"}";

        assertThat(LogSanitizer.sanitize(value, 1000))
                .isEqualTo("{\"" + field + "\":\"***\",\"message\":\"public\"}");
    }

    @Test
    void masksLongSensitiveValuesWithoutOverflow() {
        String value = "{\"apiKey\":\"" + "a".repeat(20000) + "\"}";

        assertThat(LogSanitizer.sanitize(value, 1000)).isEqualTo("{\"apiKey\":\"***\"}");
    }

    @Test
    void marksLineBreaksWithoutSplittingLogRecords() {
        assertThat(LogSanitizer.sanitize("first\r\nsecond\nthird", 1000))
                .isEqualTo("first\\nsecond\\nthird");
    }

    @Test
    void preservesEscapedQuotesInNonSensitiveFields() {
        String value = "{\"message\":\"public\\\"text\\\\end\"}";

        assertThat(LogSanitizer.sanitize(value, 1000)).isEqualTo(value);
    }
}
