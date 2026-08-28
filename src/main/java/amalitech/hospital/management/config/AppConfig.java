package amalitech.hospital.management.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * App-wide beans that don't belong to a more specific config class.
 */
@Configuration
public class AppConfig {

    /** Name referenced by both the component definition and the global requirement below —
     *  springdoc only renders Swagger UI's "Authorize" button/padlock icons when the
     *  OpenAPI spec itself declares a security scheme; it has nothing to do with whether
     *  the API is actually JWT-secured; without this, POST /api/v1/auth/login's token
     *  had no field anywhere in Swagger UI to paste it into. */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI hmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hospital Management System API")
                        .version("v1")
                        .description("### [Click here to sign in with Google](/oauth2/authorization/google)\n"
                                + "Redirects straight to Google's account picker. After you approve, this page "
                                + "will show a JSON body with your token — copy it, click \"Authorize\" above, "
                                + "paste it in (no \"Bearer \" prefix), and every protected endpoint below works.\n\n"
                                + "REST API for the HMS backend. Endpoints are namespaced /api/v1/{resource}. "
                                + "Users & role/permission management is documented first; other domains "
                                + "(patients, appointments, pharmacy, ...) follow the same convention as they're added. "
                                + "A token can also be obtained via POST /api/v1/auth/login instead of Google — "
                                + "both return the same {\"data\":{\"token\":\"...\"}} shape.\n\n"
                                + "### [Click here to try the CSRF protection demo](/docs/csrf-demo)\n"
                                + "The one path in this app where CSRF protection is deliberately left on "
                                + "(see `CsrfDemoSecurityConfig`'s Javadoc) — everywhere else is disabled since "
                                + "bearer-JWT auth gives a forged cross-site request nothing to ride on. Opens a "
                                + "page with two forms posting to the same endpoint: one carries a valid, "
                                + "session-bound token and succeeds (200); the other omits it, and Spring "
                                + "Security's `CsrfFilter` rejects it (403) before it ever reaches a controller."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Global, not per-endpoint: every endpoint here already enforces its own
                // access control via @RequirePermission/AuthorizationAspect rather than
                // Spring Security route matching (see SecurityConfig's Javadoc) — this
                // only tells Swagger UI to attach whatever token is Authorized to every
                // request's Authorization header, not that Swagger itself denies anything.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** Applies APP_PAGE_SIZE as the default page size for every paginated endpoint. */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(
            @Value("${app.page-size}") int defaultPageSize) {
        return resolver -> resolver.setFallbackPageable(PageRequest.of(0, defaultPageSize));
    }

    /**
     * Manually documents Google's OAuth2 login entry point in the generated spec.
     * Springdoc only ever introspects {@code @RestController} handler methods; this path
     * is registered by Spring Security's {@code .oauth2Login(...)} DSL
     * ({@code SecurityConfig}) instead — a filter that runs ahead of the
     * {@code DispatcherServlet}, with no annotated method for springdoc to find — so
     * without this it would never show up in Swagger at all. {@code GlobalOpenApiCustomizer}
     * is springdoc's own extension point for exactly this: adding a synthetic path to the
     * generated {@code OpenAPI} model alongside the ones it discovers automatically.
     *
     * <p>Deliberately doesn't also document {@code /login/oauth2/code/google} (the
     * callback Google itself redirects to) as its own Swagger entry — a previous pass
     * did, and it only confused callers into thinking it was something to fill in
     * {@code code}/{@code state} for and Execute manually, when it's Google's own
     * redirect target, never something a caller navigates to on its own. The one
     * endpoint below already explains what that callback does with the code.
     */
    @Bean
    public GlobalOpenApiCustomizer googleOAuth2EndpointsCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                openApi.setPaths(new Paths());
            }
            openApi.getPaths()
                    .addPathItem("/oauth2/authorization/google", new PathItem().get(
                            new Operation()
                                    .addTagsItem("Auth")
                                    .summary("Start Google OAuth2 login")
                                    .description("**[Click here to sign in with Google](/oauth2/authorization/google)** "
                                            + "(not callable from \"Try it out\" below — open the link instead). "
                                            + "Redirects to Google's consent screen; once approved, Google itself "
                                            + "redirects back to this app (never something you call directly), "
                                            + "which returns the login result as JSON: "
                                            + "{\"status\":\"success\",\"data\":{\"token\":\"...\",\"userId\":\"...\","
                                            + "\"username\":\"...\",\"roles\":[\"...\"]}} on success (same shape POST "
                                            + "/api/v1/auth/login returns), or a 401 error body if the account is "
                                            + "deactivated or has no role.")
                                    .responses(new ApiResponses()
                                            .addApiResponse("302", new ApiResponse()
                                                    .description("Redirect to Google's own consent screen")))));
        };
    }

    /**
     * Manually documents the CSRF demo page in the generated spec, for the same reason
     * {@link #googleOAuth2EndpointsCustomizer()} manually documents the OAuth2 login
     * entry point: springdoc only introspects {@code @RestController} handler methods
     * (confirmed against the live {@code /v3/api-docs} output — a plain {@code @Controller}
     * returning view names, like {@code CsrfDemoController}, never shows up there on its
     * own), so without this it would be invisible from Swagger UI entirely despite being a
     * real, reachable page.
     */
    @Bean
    public GlobalOpenApiCustomizer csrfDemoEndpointCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                openApi.setPaths(new Paths());
            }
            openApi.getPaths()
                    .addPathItem("/docs/csrf-demo", new PathItem().get(
                            new Operation()
                                    .addTagsItem("Security")
                                    .summary("CSRF protection mechanism demo")
                                    .description("**[Click here to open the demo](/docs/csrf-demo)** "
                                            + "(not callable from \"Try it out\" below — open the link instead, "
                                            + "it's an HTML page, not a JSON endpoint). Renders two forms posting "
                                            + "to the same endpoint: one with a valid, session-bound CSRF token "
                                            + "(succeeds, 200), one without (rejected by CsrfFilter, 403). This is "
                                            + "the only path in the app where CSRF protection is left on — see "
                                            + "SecurityConfig's own Javadoc for why every other endpoint disables it.")
                                    .responses(new ApiResponses()
                                            .addApiResponse("200", new ApiResponse()
                                                    .description("The demo page itself (HTML, not JSON)")))));
        };
    }
}
