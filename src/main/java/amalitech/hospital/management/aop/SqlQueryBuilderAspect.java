package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.SqlQueryBuilder;
import amalitech.hospital.management.utils.filters.QueryBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
public class SqlQueryBuilderAspect {

    private final EntityManager entityManager;

    @Around("@annotation(sqlQueryBuilder)")
    public Object executeSqlQuery(ProceedingJoinPoint pjp, SqlQueryBuilder sqlQueryBuilder) throws Throwable {
        // Example: build a query dynamically based on annotation value
        String queryName = sqlQueryBuilder.value();

        QueryBuilder builder;

        switch (queryName) {
            case "findDoctorsByDepartment":
                builder = QueryBuilder.select("d.doctor_id", "d.name", "dep.name AS department")
                        .from("doctors d")
                        .join("doctor_department dd ON dd.doctor_id = d.doctor_id")
                        .join("departments dep ON dep.department_id = dd.department_id")
                        .whereActive("d")
                        .whereActive("dep");
                break;

            case "findDepartmentsWithDoctors":
                builder = QueryBuilder.select("dep.department_id", "dep.name", "COUNT(d.doctor_id) AS doctor_count")
                        .from("departments dep")
                        .join("doctor_department dd ON dd.department_id = dep.department_id")
                        .join("doctors d ON d.doctor_id = dd.doctor_id")
                        .groupBy("dep.department_id", "dep.name")
                        .having("COUNT(d.doctor_id) > 0");
                break;

            case "findRolesWithPermissionCount":
                builder = QueryBuilder.select("r.role_id", "r.role_name", "COUNT(rp.permission_id) AS permission_count")
                        .from("roles r")
                        .leftJoin("role_permissions rp ON rp.role_id = r.role_id AND rp.deleted_at IS NULL")
                        .whereActive("r")
                        .groupBy("r.role_id", "r.role_name");
                break;

            default:
                throw new IllegalStateException("Unknown query builder key: " + queryName);
        }

        String sql = builder.build();
        Query query = entityManager.createNativeQuery(sql);

        // Execute and return results
        List<?> results = query.getResultList();
        return results;
    }
}
