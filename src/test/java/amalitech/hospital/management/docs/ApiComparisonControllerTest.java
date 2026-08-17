package amalitech.hospital.management.docs;

import amalitech.hospital.management.controller.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reuses {@link AbstractControllerTest} purely for its real-Spring-context {@code MockMvc} —
 * this page has no auth requirement and no fixtures to build, unlike every other subclass.
 */
class ApiComparisonControllerTest extends AbstractControllerTest {

    @Test
    void benchmarkPage_isReachableWithoutAuthenticationAndRendersAsHtml() throws Exception {
        // No Authorization header at all — this is a reference page, not a protected API
        // resource, so it must never require a token the way every /api/v1/** endpoint does.
        mockMvc.perform(get("/docs/rest-vs-graphql"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void benchmarkPage_rendersEveryOperationAndTheAnalysisSummary() throws Exception {
        mockMvc.perform(get("/docs/rest-vs-graphql"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("REST vs GraphQL")))
                .andExpect(content().string(containsString("Get Doctor by id")))
                .andExpect(content().string(containsString("Get Role by id")))
                .andExpect(content().string(containsString("Create Doctor")))
                .andExpect(content().string(containsString("Delete Doctor")))
                .andExpect(content().string(containsString("Benchmark Results")))
                .andExpect(content().string(containsString("Analysis")));
    }

    @Test
    void benchmarkPage_rendersBothLatencyCharts() throws Exception {
        mockMvc.perform(get("/docs/rest-vs-graphql"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("REST — Avg Latency")))
                .andExpect(content().string(containsString("GraphQL — Avg Latency")));
    }
}
