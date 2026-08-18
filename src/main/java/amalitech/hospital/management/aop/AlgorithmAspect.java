package amalitech.hospital.management.aop;

import amalitech.hospital.management.annotation.ApplyAlgorithm;
import amalitech.hospital.management.utils.AlgorithmUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Aspect
@Component
public class AlgorithmAspect {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmAspect.class);

    // Fires for every @ApplyAlgorithm call — e.g. RoleService.sort (via getRolePermissions)
    // or AppointmentService.sort/search (via throwIfDoctorDoubleBooked).
    @Around("@annotation(applyAlgorithm)")
    public Object executeAlgorithm(ProceedingJoinPoint pjp, ApplyAlgorithm applyAlgorithm) throws Throwable {
        log.debug("AlgorithmAspect.executeAlgorithm invoked — called by the @ApplyAlgorithm-annotated service method's self-proxy call");
        Object[] args = pjp.getArgs();
        String algorithm = applyAlgorithm.value();

        if ("mergeSort".equalsIgnoreCase(algorithm)
                && args.length > 1 && args[0] instanceof List && args[1] instanceof Comparator) {
            List<?> list = (List<?>) args[0];
            Comparator<?> comparator = (Comparator<?>) args[1];
            AlgorithmUtils.mergeSort(list, (Comparator) comparator);
            return list;
        }

        if ("binarySearch".equalsIgnoreCase(algorithm)
                && args.length > 2 && args[0] instanceof List && args[1] != null && args[2] instanceof Function) {
            List<?> list = (List<?>) args[0];
            Object targetKey = args[1];
            Function<?, ?> keyExtractor = (Function<?, ?>) args[2];
            return AlgorithmUtils.binarySearch(list, targetKey, (Function) keyExtractor);
        }

        // fallback: args didn't match the expected shape — just run the original method
        return pjp.proceed();
    }
}
