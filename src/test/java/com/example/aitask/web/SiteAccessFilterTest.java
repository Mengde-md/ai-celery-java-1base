package com.example.aitask.web;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SiteAccessFilterTest {
    private final SiteAccessFilter filter = new SiteAccessFilter("888", JsonMapper.builder().build());

    @Test
    void statusDoesNotExposeTheCode() throws Exception {
        var response = request("GET", "/api/site-access", null, null, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"required\":true", "\"granted\":false")
                .doesNotContain("888");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void taskApisRequireTheSiteCode() throws Exception {
        for (String path : new String[]{"/api/v1/tasks", "/api/v1/tasks/demo/cancel", "/api/v1/tasks/demo"}) {
            MockFilterChain chain = new MockFilterChain();
            var response = request("GET", path, null, null, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("SITE_ACCESS_REQUIRED");
            assertThat(chain.getRequest()).isNull();
        }
    }

    @Test
    void wrongAndMissingCodesCannotGrantAccess() throws Exception {
        for (String body : new String[]{"{\"code\":\"wrong\"}", "{}", "{\"code\":\"\"}"}) {
            var response = request("POST", "/api/site-access", body, null, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("Set-Cookie")).isNull();
        }
    }

    @Test
    void correctCodeSetsAnOpaqueHttpOnlySecureCookie() throws Exception {
        var response = request("POST", "/api/site-access", "{\"code\":\"888\"}", null,
                new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"granted\":true");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("cd_site_access=", "HttpOnly", "Secure", "SameSite=Lax", "Max-Age=28800");
        assertThat(response.getCookie(SiteAccessFilter.COOKIE_NAME).getValue()).isNotEqualTo("888");
    }

    @Test
    void grantedCookieAllowsTaskHandling() throws Exception {
        Cookie cookie = grant();
        MockFilterChain chain = new MockFilterChain();
        var response = request("GET", "/api/v1/tasks", null, cookie, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        var status = request("GET", "/api/site-access", null, cookie, new MockFilterChain());
        assertThat(status.getContentAsString()).contains("\"granted\":true");
    }

    @Test
    void tamperedExpiredAndPreviousProcessCookiesAreRejected() throws Exception {
        for (String token : new String[]{"888", "1.invalid", "not-a-time.signature", grant().getValue() + "x"}) {
            var response = request("GET", "/api/v1/tasks", null,
                    new Cookie(SiteAccessFilter.COOKIE_NAME, token), new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401);
        }
        var restarted = new SiteAccessFilter("888", JsonMapper.builder().build());
        var request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.setCookies(grant());
        var response = new MockHttpServletResponse();
        restarted.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void malformedAndOversizedInputsAreRejected() throws Exception {
        for (String body : new String[]{"not-json", "x".repeat(1025)}) {
            var response = request("POST", "/api/site-access", body, null, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getHeader("Set-Cookie")).isNull();
        }
    }

    @Test
    void healthChecksRemainAvailable() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        request("GET", "/actuator/health", null, null, chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    private Cookie grant() throws Exception {
        return request("POST", "/api/site-access", "{\"code\":\"888\"}", null,
                new MockFilterChain()).getCookie(SiteAccessFilter.COOKIE_NAME);
    }

    private MockHttpServletResponse request(String method, String path, String body, Cookie cookie,
                                            MockFilterChain chain) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setSecure(true);
        if (body != null) request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (cookie != null) request.setCookies(cookie);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
