package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.config.graphql.GraphQlConfig;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.service.LabResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Slice test for {@link LabResultResolver} — see {@code UserResolverTest}'s Javadoc for
 *  the shared reasoning. */
@GraphQlTest(LabResultResolver.class)
@Import(GraphQlConfig.class)
class LabResultResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private LabResultService labResultService;

    private LabResultResponse existingResult() {
        LabResultResponse response = new LabResultResponse();
        response.setLabResultId("result-1");
        response.setLabOrderId("lab-1");
        response.setResultValue("5.2");
        response.setIsAbnormal(false);
        return response;
    }

    @Test
    void labResult_returnsMappedResponse() {
        when(labResultService.getResult("lab-1")).thenReturn(existingResult());

        graphQlTester.document("{ labResult(labOrderId: \"lab-1\") { resultValue isAbnormal } }")
                .execute()
                .path("labResult.resultValue").entity(String.class).isEqualTo("5.2")
                .path("labResult.isAbnormal").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void createLabResult_delegatesToService() {
        when(labResultService.createResult(any(), any())).thenReturn(existingResult());

        graphQlTester.document("mutation { createLabResult(labOrderId: \"lab-1\", input: { resultValue: \"5.2\" }) { labResultId } }")
                .execute()
                .path("createLabResult.labResultId").entity(String.class).isEqualTo("result-1");
    }

    @Test
    void deleteLabResult_returnsTrue() {
        graphQlTester.document("mutation { deleteLabResult(labOrderId: \"lab-1\") }")
                .execute()
                .path("deleteLabResult").entity(Boolean.class).isEqualTo(true);

        verify(labResultService).deleteResult("lab-1");
    }
}
