package amalitech.hospital.management.annotation;

import java.lang.annotation.*;

/**
 * Custom annotation to mark methods that should execute
 * a SQL query built via QueryBuilder.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlQueryBuilder {

    /**
     * Base query key or name.
     * Example: "findDoctorsByDepartment"
     */
    String value() default "";

    /**
     * Table name to start FROM clause.
     */
    String from() default "";

    /**
     * Columns to SELECT.
     */
    String[] select() default {};

    /**
     * Optional WHERE conditions.
     */
    String[] where() default {};

    /**
     * Optional JOIN clauses.
     */
    String[] joins() default {};
}
