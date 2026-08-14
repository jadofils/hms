package amalitech.hospital.management.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FindUserData {
    /**
     * Search by userId.
     */
    String userId() default "";

    /**
     * Search by username.
     */
    String username() default "";

    /**
     * Which domain to query: "user", "role", "permission", "appointment", "doctor".
     */
    String domain() default "user";
}
