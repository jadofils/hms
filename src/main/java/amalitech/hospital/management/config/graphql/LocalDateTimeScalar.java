package amalitech.hospital.management.config.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * A hand-rolled {@code LocalDateTime} scalar, registered by {@link GraphQlConfig} —
 * deliberately not reusing {@code graphql-java-extended-scalars}' own {@code DateTime}
 * scalar, whose {@code Coercing} only accepts {@link java.time.OffsetDateTime}/
 * {@link java.time.ZonedDateTime} (confirmed by inspecting {@code DateTimeScalar}'s
 * compiled {@code Coercing} at the version this project pins). Every date-time field in
 * this domain (`appointmentDate`, `createdAt`, `issuedAt`, ...) is a plain
 * {@link LocalDateTime} with no zone/offset at all — matching that would mean converting
 * to/from a zone at every single resolver boundary for no benefit this application
 * actually needs. Serializes/parses as a plain ISO-8601 local date-time string, e.g.
 * {@code "2026-01-01T10:00:00"}.
 */
final class LocalDateTimeScalar {

    private LocalDateTimeScalar() {
    }

    static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("LocalDateTime")
            .description("An ISO-8601 local date-time with no time zone, e.g. 2026-01-01T10:00:00")
            .coercing(new Coercing<LocalDateTime, String>() {

                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale)
                        throws CoercingSerializeException {
                    if (dataFetcherResult instanceof LocalDateTime localDateTime) {
                        return localDateTime.toString();
                    }
                    throw new CoercingSerializeException(
                            "Expected a LocalDateTime, got " + dataFetcherResult.getClass().getSimpleName());
                }

                @Override
                public LocalDateTime parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
                        throws CoercingParseValueException {
                    try {
                        return LocalDateTime.parse(input.toString());
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseValueException("Not a valid ISO-8601 local date-time: " + input, e);
                    }
                }

                @Override
                public LocalDateTime parseLiteral(Value<?> input, CoercedVariables variables,
                        GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
                    if (input instanceof StringValue stringValue) {
                        try {
                            return LocalDateTime.parse(stringValue.getValue());
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseLiteralException(
                                    "Not a valid ISO-8601 local date-time: " + stringValue.getValue(), e);
                        }
                    }
                    throw new CoercingParseLiteralException("Expected a StringValue literal");
                }
            })
            .build();
}
