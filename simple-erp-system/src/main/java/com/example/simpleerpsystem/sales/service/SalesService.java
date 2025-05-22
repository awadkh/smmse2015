package com.example.simpleerpsystem.sales.service;

import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.repository.ProductRepository;
import com.example.simpleerpsystem.sales.entity.AdditionalServiceItem;
import com.example.simpleerpsystem.sales.entity.InvoiceItem;
import com.example.simpleerpsystem.sales.entity.SalesInvoice;
import com.example.simpleerpsystem.sales.entity.enums.AdditionalServiceCostType;
import com.example.simpleerpsystem.sales.entity.enums.PaymentStatus;
import com.example.simpleerpsystem.sales.entity.enums.SaleType;
import com.example.simpleerpsystem.sales.repository.AdditionalServiceItemRepository;
import com.example.simpleerpsystem.sales.repository.InvoiceItemRepository;
import com.example.simpleerpsystem.sales.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Placeholder for future, not directly used with R2DBC without operator
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final ProductRepository productRepository; // As per initial instruction

    private Mono<SalesInvoice> populateSalesInvoiceDetails(SalesInvoice invoice) {
        if (invoice == null) {
            return Mono.empty();
        }
        Mono<List<InvoiceItem>> itemsMono = invoiceItemRepository.findBySalesInvoiceId(invoice.getId()).collectList();
        Mono<List<AdditionalServiceItem>> additionalServicesMono = additionalServiceItemRepository.findBySalesInvoiceId(invoice.getId()).collectList();

        return Mono.zip(itemsMono, additionalServicesMono, (items, additionalServices) -> {
            invoice.setItems(items);
            invoice.setAdditionalServices(additionalServices);
            return invoice;
        });
    }

    public Flux<SalesInvoice> getAllSalesInvoices() {
        return salesInvoiceRepository.findAll()
                .flatMap(this::populateSalesInvoiceDetails);
    }

    public Mono<SalesInvoice> getSalesInvoiceById(Long id) {
        return salesInvoiceRepository.findById(id)
                .flatMap(this::populateSalesInvoiceDetails);
    }

    public Mono<Void> deleteSalesInvoice(Long id) {
        // Child items/services are expected to be deleted by CASCADE constraints in the DB
        return salesInvoiceRepository.deleteById(id);
    }

    // Using salesInvoiceInput to distinguish from the salesInvoice variable being built
    public Mono<SalesInvoice> createSalesInvoice(SalesInvoice salesInvoiceInput) {
        // 0. Set initial fields
        if (salesInvoiceInput.getInvoiceDate() == null) {
            salesInvoiceInput.setInvoiceDate(LocalDateTime.now());
        }
        if (salesInvoiceInput.getSaleType() == SaleType.CASH && salesInvoiceInput.getPaymentStatus() == null) {
            salesInvoiceInput.setPaymentStatus(PaymentStatus.PENDING_CASH);
        } else if (salesInvoiceInput.getSaleType() == SaleType.INSTALLMENT && salesInvoiceInput.getPaymentStatus() == null) {
            salesInvoiceInput.setPaymentStatus(PaymentStatus.PENDING_INSTALLMENT);
        }
        salesInvoiceInput.setId(null); // Ensure creation

        // 1. Process InvoiceItems: Fetch product details, calculate subtotals, check stock
        final List<InvoiceItem> processedItems = new ArrayList<>(); // To store items after successful processing
        if (salesInvoiceInput.getItems() == null) {
            salesInvoiceInput.setItems(Collections.emptyList()); // Ensure not null for iteration
        }

        Mono<Double> overallSubTotalMono = Flux.fromIterable(salesInvoiceInput.getItems())
            .concatMap(item -> {
                item.setId(null); // Ensure item is treated as new
                return productRepository.findById(item.getProductId())
                    .switchIfEmpty(Mono.error(new RuntimeException("Product not found with ID: " + item.getProductId())))
                    .handle((product, sink) -> {
                        if (product.getQuantityOnHand() < item.getQuantity()) {
                            sink.error(new RuntimeException("Insufficient stock for product ID: " + product.getId() + " (" + product.getName() + "). Available: " + product.getQuantityOnHand() + ", Requested: " + item.getQuantity()));
                        } else {
                            item.setProductName(product.getName());
                            item.setUnitPrice(product.getPrice());
                            item.setSubtotal(item.getUnitPrice() * item.getQuantity());
                            processedItems.add(item); // Add to the temporary list of successfully processed items
                            sink.next(item.getSubtotal());
                        }
                    });
            })
            .cast(Double.class) // Ensure the stream is of Doubles for reduction
            .reduce(0.0, Double::sum);

        return overallSubTotalMono.flatMap(overallSubTotal -> {
            salesInvoiceInput.setSubTotalAmount(overallSubTotal);

            // 2. Calculate Discount
            double amountAfterItemDiscount = overallSubTotal;
            if (salesInvoiceInput.getDiscountPercentage() != null && salesInvoiceInput.getDiscountPercentage() > 0) {
                amountAfterItemDiscount -= overallSubTotal * salesInvoiceInput.getDiscountPercentage();
            } else if (salesInvoiceInput.getDiscountAmount() != null && salesInvoiceInput.getDiscountAmount() > 0) {
                amountAfterItemDiscount -= salesInvoiceInput.getDiscountAmount();
            }
            amountAfterItemDiscount = Math.max(0, amountAfterItemDiscount); // Ensure not negative

            // 3. Process AdditionalServiceItems
            double overallAdditionalServicesTotal = 0;
            if (salesInvoiceInput.getAdditionalServices() != null) {
                for (AdditionalServiceItem asi : salesInvoiceInput.getAdditionalServices()) {
                    asi.setId(null); // Ensure ASI is treated as new
                    if (asi.getCostType() == AdditionalServiceCostType.FIXED_AMOUNT) {
                        asi.setCalculatedCost(asi.getValue());
                    } else if (asi.getCostType() == AdditionalServiceCostType.PERCENTAGE_OF_TOTAL) {
                        asi.setCalculatedCost(amountAfterItemDiscount * asi.getValue());
                    }
                    overallAdditionalServicesTotal += (asi.getCalculatedCost() != null ? asi.getCalculatedCost() : 0.0);
                }
            }
            salesInvoiceInput.setAdditionalServicesTotalAmount(overallAdditionalServicesTotal);

            // 4. Calculate Final Total Amount
            salesInvoiceInput.setTotalAmount(amountAfterItemDiscount + overallAdditionalServicesTotal);

            // 5. Save SalesInvoice, then Items, then AdditionalServices, then Update Stock
            // Create a new SalesInvoice instance for saving to avoid mutating the input directly in this stage
            SalesInvoice invoiceToSave = SalesInvoice.builder()
                .customerId(salesInvoiceInput.getCustomerId())
                .invoiceDate(salesInvoiceInput.getInvoiceDate())
                .saleType(salesInvoiceInput.getSaleType())
                .discountAmount(salesInvoiceInput.getDiscountAmount())
                .discountPercentage(salesInvoiceInput.getDiscountPercentage())
                .subTotalAmount(salesInvoiceInput.getSubTotalAmount())
                .additionalServicesTotalAmount(salesInvoiceInput.getAdditionalServicesTotalAmount())
                .totalAmount(salesInvoiceInput.getTotalAmount())
                .paymentStatus(salesInvoiceInput.getPaymentStatus())
                .build();


            return salesInvoiceRepository.save(invoiceToSave)
                .flatMap(savedInvoice -> {
                    Long invoiceId = savedInvoice.getId();

                    // Save InvoiceItems and Update Stock
                    Mono<List<InvoiceItem>> savedItemsMono = Flux.fromIterable(processedItems) // Use the list populated during stock check
                        .flatMap(item -> {
                            item.setSalesInvoiceId(invoiceId);
                            // item.setId(null); // Already done above
                            return invoiceItemRepository.save(item)
                                .flatMap(savedItem -> productRepository.findById(savedItem.getProductId())
                                    .flatMap(p -> {
                                        p.setQuantityOnHand(p.getQuantityOnHand() - savedItem.getQuantity());
                                        return productRepository.save(p).thenReturn(savedItem);
                                    }));
                        }).collectList();

                    // Save AdditionalServiceItems
                    Mono<List<AdditionalServiceItem>> savedAdditionalServicesMono;
                    if (salesInvoiceInput.getAdditionalServices() != null && !salesInvoiceInput.getAdditionalServices().isEmpty()) {
                        savedAdditionalServicesMono = Flux.fromIterable(salesInvoiceInput.getAdditionalServices())
                            .flatMap(asi -> {
                                asi.setSalesInvoiceId(invoiceId);
                                // asi.setId(null); // Already done above
                                return additionalServiceItemRepository.save(asi);
                            }).collectList();
                    } else {
                        savedAdditionalServicesMono = Mono.just(Collections.emptyList());
                    }
                    
                    return Mono.zip(savedItemsMono, savedAdditionalServicesMono)
                        .map(tuple -> {
                            savedInvoice.setItems(tuple.getT1());
                            savedInvoice.setAdditionalServices(tuple.getT2());
                            return savedInvoice;
                        });
                });
        });
    }
}
