package amalitech.hospital.management.controller;

import amalitech.hospital.management.annotation.RequirePermission;
import amalitech.hospital.management.dto.common.ApiResult;
import amalitech.hospital.management.dto.finance.InvoiceRequest;
import amalitech.hospital.management.dto.finance.InvoiceResponse;
import amalitech.hospital.management.enums.PermissionAction;
import amalitech.hospital.management.enums.Resource;
import amalitech.hospital.management.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Invoice management — backed by {@link InvoiceService}. See that class for
 * caching/exception/transaction behavior; this layer only maps HTTP <-> DTOs.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Billing invoices per appointment")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @Operation(summary = "List invoices (paginated, sortable)",
            description = "Standard `?sort=property,direction` query param (e.g. `sort=issuedAt,desc`) "
                    + "— backed directly by Spring Data JPA, so any `Invoice` field is sortable: "
                    + "`invoiceId`, `totalAmount`, `paymentStatus`, `issuedAt`, `updatedAt`. Unlike "
                    + "`/api/v1/users`, an unrecognized property is not validated ahead of time and "
                    + "currently surfaces as a 400 rather than silently falling back.")
    @ApiResponse(responseCode = "200", description = "Invoices returned")
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "Sort by property,direction. Possible properties: invoiceId, totalAmount, "
                    + "paymentStatus, issuedAt, updatedAt.",
            array = @ArraySchema(schema = @Schema(type = "string")), example = "issuedAt,desc")
    @RequirePermission(resource = Resource.INVOICES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<PagedModel<InvoiceResponse>>> getInvoices(Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Invoices retrieved", invoiceService.getInvoices(pageable)));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get an invoice by id")
    @ApiResponse(responseCode = "200", description = "Invoice found")
    @ApiResponse(responseCode = "404", description = "Invoice not found")
    @RequirePermission(resource = Resource.INVOICES, action = PermissionAction.READ)
    public ResponseEntity<ApiResult<InvoiceResponse>> getInvoice(
            @Parameter(description = "Invoice UUID") @PathVariable String invoiceId) {
        return ResponseEntity.ok(ApiResult.of("Invoice retrieved", invoiceService.getInvoice(invoiceId)));
    }

    @PostMapping
    @Operation(summary = "Create an invoice")
    @ApiResponse(responseCode = "201", description = "Invoice created")
    @ApiResponse(responseCode = "404", description = "Appointment or patient not found")
    @RequirePermission(resource = Resource.INVOICES, action = PermissionAction.CREATE)
    public ResponseEntity<ApiResult<InvoiceResponse>> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Invoice created", invoiceService.createInvoice(request)));
    }

    @PutMapping("/{invoiceId}")
    @Operation(summary = "Update an invoice")
    @ApiResponse(responseCode = "200", description = "Invoice updated")
    @ApiResponse(responseCode = "404", description = "Invoice, appointment, or patient not found")
    @RequirePermission(resource = Resource.INVOICES, action = PermissionAction.UPDATE)
    public ResponseEntity<ApiResult<InvoiceResponse>> updateInvoice(
            @Parameter(description = "Invoice UUID") @PathVariable String invoiceId,
            @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(ApiResult.of("Invoice updated", invoiceService.updateInvoice(invoiceId, request)));
    }

    @DeleteMapping("/{invoiceId}")
    @Operation(summary = "Delete an invoice")
    @ApiResponse(responseCode = "204", description = "Invoice deleted")
    @ApiResponse(responseCode = "404", description = "Invoice not found")
    @RequirePermission(resource = Resource.INVOICES, action = PermissionAction.DELETE)
    public ResponseEntity<Void> deleteInvoice(
            @Parameter(description = "Invoice UUID") @PathVariable String invoiceId) {
        invoiceService.deleteInvoice(invoiceId);
        return ResponseEntity.noContent().build();
    }
}
