package amalitech.hospital.management.docs;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the empirical REST-vs-GraphQL benchmark at {@code GET /docs/rest-vs-graphql} as a
 * server-rendered Thymeleaf page — the one real caller here is the team itself, consulting this
 * page while deciding which style a new operation belongs behind, per
 * {@link ApiComparisonCatalog}'s Javadoc on where the numbers come from.
 *
 * A plain {@code @Controller} (not {@code @RestController}) since it returns a view name for
 * Spring's Thymeleaf {@code ViewResolver} to render, not a JSON body — the only place in this
 * codebase that does, since every {@code *Controller} under {@code controller/} is a
 * DTO-mapping REST endpoint per CLAUDE.md's layering. Kept in its own {@code docs} package
 * rather than alongside those for exactly that reason: this isn't a REST resource endpoint, and
 * mixing the two package would blur that boundary.
 */
@Controller
@RequiredArgsConstructor
public class ApiComparisonController {

    private final ApiComparisonCatalog catalog;

    @GetMapping("/docs/rest-vs-graphql")
    public String compare(Model model) {
        model.addAttribute("benchmarkResults", catalog.benchmarkResults());
        model.addAttribute("maxAvgLatencyMs", catalog.maxAvgLatencyMs());
        model.addAttribute("restWinCount", catalog.restWinCount());
        model.addAttribute("avgRestMs", catalog.avgRestMs());
        model.addAttribute("avgGraphQlMs", catalog.avgGraphQlMs());
        model.addAttribute("iterations", ApiComparisonCatalog.ITERATIONS_PER_OPERATION);
        return "api-comparison";
    }
}
