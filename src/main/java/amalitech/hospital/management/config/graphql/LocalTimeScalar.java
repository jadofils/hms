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

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * A hand-rolled {@code LocalTime} scalar — same reasoning as {@link LocalDateTimeScalar}:
 * {@code graphql-java-extended-scalars}' own {@code Time} scalar's {@code Coercing} only
 * accepts {@link java.time.OffsetTime}, but {@code DoctorSchedule.startTime}/
 * {@code endTime} are plain {@link LocalTime} with no zone/offset. Serializes/parses as a
 * plain ISO-8601 local time string, e.g. {@code "09:00:00"}.
 */
final class LocalTimeScalar {

    private LocalTimeScalar() {
    }

    static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("LocalTime")
            .description("An ISO-8601 local time of day with no time zone, e.g. 09:00:00")
            .coercing(new Coercing<LocalTime, String>() {

                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale)
                        throws CoercingSerializeException {
                    if (dataFetcherResult instanceof LocalTime localTime) {
                        return localTime.toString();
                    }
                    throw new CoercingSerializeException(
                            "Expected a LocalTime, got " + dataFetcherResult.getClass().getSimpleName());
                }

                @Override
                public LocalTime parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
                        throws CoercingParseValueException {
                    try {
                        return LocalTime.parse(input.toString());
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseValueException("Not a valid ISO-8601 local time: " + input, e);
                    }
                }

                @Override
                public LocalTime parseLiteral(Value<?> input, CoercedVariables variables,
                        GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
                    if (input instanceof StringValue stringValue) {
                        try {
                            return LocalTime.parse(stringValue.getValue());
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseLiteralException(
                                    "Not a valid ISO-8601 local time: " + stringValue.getValue(), e);
                        }
                    }
                    throw new CoercingParseLiteralException("Expected a StringValue literal");
                }
            })
            .build();
}
