package amalitech.hospital.management.config.graphql;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers the custom scalars this application's schema files declare —
 * {@code Date}, {@code LocalDateTime}, {@code LocalTime}, and {@code BigDecimal} — none of which
 * graphql-java defines out of the box (its spec only covers {@code String}/{@code Int}/
 * {@code Float}/{@code Boolean}/{@code ID}).
 *
 * <p>This is the only hand-written piece of "schema merging" this application needs.
 * Every {@code *.graphqls} file under {@code spring.graphql.schema.locations}
 * (see {@code application.yaml}) is already parsed and merged into one
 * {@code TypeDefinitionRegistry} automatically by Spring for GraphQL's own
 * auto-configuration — splitting the schema into {@code common}/{@code user}/
 * {@code patient}/{@code doctor}/{@code appointment}/{@code pharmacy}/{@code lab}/
 * {@code finance}/{@code notification} files (mirroring this codebase's own
 * {@code model}/{@code service}/{@code controller} package boundaries) needs no extra
 * merge code at all — a cross-file type reference (e.g. {@code appointment.graphqls}'
 * {@code patient: Patient} field) resolves the same way a Java import resolves a class
 * from another package. The only gap the framework's own defaults don't cover is exactly
 * these three scalars, since a scalar's Coercing has to be registered in code, not SDL.
 */
@Configuration
public class GraphQlConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.Date)
                .scalar(LocalDateTimeScalar.INSTANCE)
                .scalar(LocalTimeScalar.INSTANCE)
                .scalar(ExtendedScalars.GraphQLBigDecimal);
    }
}
