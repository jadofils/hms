package amalitech.hospital.management.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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

    @Bean
    public OpenAPI hmsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Hospital Management System API")
                .version("v1")
                .description("REST API for the HMS backend. Endpoints are namespaced /api/v1/{resource}. "
                        + "Users & role/permission management is documented first; other domains "
                        + "(patients, appointments, pharmacy, ...) follow the same convention as they're added."));
    }

    /** Applies APP_PAGE_SIZE as the default page size for every paginated endpoint. */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(
            @Value("${app.page-size}") int defaultPageSize) {
        return resolver -> resolver.setFallbackPageable(PageRequest.of(0, defaultPageSize));
    }
}
