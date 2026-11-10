package com.example.aitask.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesValidCallerId() throws Exception {
        assertThat(filterRequest("trace:example-123")).isEqualTo("trace:example-123");
    }

    @Test
    void generatesAnIdForAnOversizedOrMalformedValue() throws Exception {
        for (String supplied : new String[] {"a".repeat(129), "two words"}) {
            String id = filterRequest(supplied);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        }
    }

    private String filterRequest(String id) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.addHeader(RequestIdFilter.HEADER, id);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader(RequestIdFilter.HEADER)));

        assertThat(MDC.get("requestId")).isNull();
        return response.getHeader(RequestIdFilter.HEADER);
    }
}
