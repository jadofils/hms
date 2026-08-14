package amalitech.hospital.management.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplyAlgorithm {
    /**
     * Which algorithm to apply: "mergeSort" or "binarySearch".
     */
    String value();

    /**
     * Optional: specify the key field for binary search.
     */
    String key() default "";
}
