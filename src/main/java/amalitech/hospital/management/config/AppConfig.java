package amalitech.hospital.management.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                        .description("REST API for the HMS backend. Endpoints are namespaced /api/v1/{resource}. "
                                + "Users & role/permission management is documented first; other domains "
                                + "(patients, appointments, pharmacy, ...) follow the same convention as they're added. "
                                + "Call POST /api/v1/auth/login, then click \"Authorize\" above and paste the "
                                + "returned token (no \"Bearer \" prefix needed) to try out protected endpoints."))
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
}
