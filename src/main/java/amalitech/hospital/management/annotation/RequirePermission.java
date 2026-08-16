package amalitech.hospital.management.annotation;

import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;

import java.lang.annotation.*;

/**
 * Declares the {@code resource:action} permission a caller's role must currently hold
 * to reach the annotated controller method — see {@code aop.AuthorizationAspect}, which
 * intercepts every call, checks {@code Resource:action} against the caller's role via the
 * {@code Role}/{@code Permission}/{@code RolePermission} tables, and rejects before the
 * method body (and therefore the service/DB layer) ever runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    Resource resource();
    PermissionAction action();
}
