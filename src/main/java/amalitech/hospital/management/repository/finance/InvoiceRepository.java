package amalitech.hospital.management.repository.finance;

import amalitech.hospital.management.model.finance.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
}
