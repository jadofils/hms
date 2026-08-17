package amalitech.hospital.management.resolvers;

import amalitech.hospital.management.dto.doctor.DoctorResponse;
import amalitech.hospital.management.dto.lab.LabOrderRequest;
import amalitech.hospital.management.dto.lab.LabOrderResponse;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.dto.patient.AppointmentResponse;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.service.AppointmentService;
import amalitech.hospital.management.service.DoctorService;
import amalitech.hospital.management.service.LabOrderService;
import amalitech.hospital.management.service.LabResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * GraphQL front door for {@link LabOrderService} — see {@code UserResolver}'s Javadoc for
 * the shared reasoning (same service layer as REST, same request DTOs). The result is
 * managed separately (see {@code LabResultResolver}), same split as REST's
 * {@code LabOrderController}/{@code LabResultController}.
 */
@Controller
@Validated
@RequiredArgsConstructor
public class LabOrderResolver {

    private final LabOrderService labOrderService;
    private final AppointmentService appointmentService;
    private final DoctorService doctorService;
    private final LabResultService labResultService;

    @QueryMapping
    public List<LabOrderResponse> labOrders(@Argument int page, @Argument int size) {
        return labOrderService.getLabOrders(PageRequest.of(page, size)).getContent();
    }

    @QueryMapping
    public LabOrderResponse labOrder(@Argument String labOrderId) {
        return labOrderService.getLabOrder(labOrderId);
    }

    @MutationMapping
    public LabOrderResponse createLabOrder(@Argument @Valid LabOrderRequest input) {
        return labOrderService.createLabOrder(input);
    }

    @MutationMapping
    public LabOrderResponse updateLabOrder(@Argument String labOrderId, @Argument @Valid LabOrderRequest input) {
        return labOrderService.updateLabOrder(labOrderId, input);
    }

    @MutationMapping
    public boolean deleteLabOrder(@Argument String labOrderId) {
        labOrderService.deleteLabOrder(labOrderId);
        return true;
    }

    @SchemaMapping(typeName = "LabOrder", field = "appointment")
    public AppointmentResponse appointment(LabOrderResponse labOrder) {
        return appointmentService.getAppointment(labOrder.getAppointmentId());
    }

    @SchemaMapping(typeName = "LabOrder", field = "doctor")
    public DoctorResponse doctor(LabOrderResponse labOrder) {
        return doctorService.getDoctor(labOrder.getDoctorId());
    }

    /** {@code result} is nullable in the schema — a lab order with no result recorded
     *  yet is the normal, expected state, not an error. */
    @SchemaMapping(typeName = "LabOrder", field = "result")
    public LabResultResponse result(LabOrderResponse labOrder) {
        try {
            return labResultService.getResult(labOrder.getLabOrderId());
        } catch (NotFoundException e) {
            return null;
        }
    }
}
