package com.example.simpleerpsystem.sales.service;

import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.entity.enums.ProductCategory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SalesServiceTest {

    @Mock
    private SalesInvoiceRepository salesInvoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    @Mock
    private ProductRepository productRepository;

    private SalesService salesService;

    @BeforeEach
    void setUp() {
        salesService = new SalesService(salesInvoiceRepository, invoiceItemRepository, additionalServiceItemRepository, productRepository);
    }

    @Test
    void testCreateSalesInvoice_Success_CashSale_NoDiscount_NoAdditionalServices() {
        Product product1 = new Product(1L, "P1", "D1", 100.0, 20, ProductCategory.WATER_FILTER, 5);
        InvoiceItem item1 = InvoiceItem.builder().productId(1L).quantity(2).build();
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L)
                .saleType(SaleType.CASH)
                .items(List.of(item1))
                .build();

        SalesInvoice savedInvoiceShell = SalesInvoice.builder()
                .id(100L)
                .customerId(1L)
                .saleType(SaleType.CASH)
                .invoiceDate(invoiceInput.getInvoiceDate() != null ? invoiceInput.getInvoiceDate() : LocalDateTime.now()) // Will be set by service
                .paymentStatus(PaymentStatus.PENDING_CASH) // Will be set by service
                .subTotalAmount(200.0)
                .additionalServicesTotalAmount(0.0)
                .totalAmount(200.0)
                .build();

        InvoiceItem savedItem1 = InvoiceItem.builder().id(1001L).salesInvoiceId(100L).productId(1L).quantity(2).unitPrice(100.0).subtotal(200.0).productName("P1").build();
        Product product1AfterSale = new Product(1L, "P1", "D1", 100.0, 18, ProductCategory.WATER_FILTER, 5);

        when(productRepository.findById(1L)).thenReturn(Mono.just(product1));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(Mono.just(savedInvoiceShell));
        when(invoiceItemRepository.save(any(InvoiceItem.class))).thenReturn(Mono.just(savedItem1));
        when(productRepository.save(any(Product.class))).thenReturn(Mono.just(product1AfterSale));

        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .assertNext(inv -> {
                    assertEquals(100L, inv.getId());
                    assertEquals(200.0, inv.getSubTotalAmount());
                    assertEquals(0.0, inv.getAdditionalServicesTotalAmount());
                    assertEquals(200.0, inv.getTotalAmount());
                    assertEquals(PaymentStatus.PENDING_CASH, inv.getPaymentStatus());
                    assertNotNull(inv.getInvoiceDate());
                    assertEquals(1, inv.getItems().size());
                    assertEquals(1001L, inv.getItems().get(0).getId());
                    assertEquals(100L, inv.getItems().get(0).getSalesInvoiceId());
                })
                .verifyComplete();

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(1)).save(productCaptor.capture());
        assertEquals(18, productCaptor.getValue().getQuantityOnHand());
    }

    @Test
    void testCreateSalesInvoice_ProductNotFound() {
        InvoiceItem item1 = InvoiceItem.builder().productId(99L).quantity(1).build(); // Non-existent product
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L)
                .saleType(SaleType.CASH)
                .items(List.of(item1))
                .build();

        when(productRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Product not found with ID: 99"))
                .verify();

        verify(salesInvoiceRepository, never()).save(any());
        verify(invoiceItemRepository, never()).save(any());
    }

    @Test
    void testCreateSalesInvoice_InsufficientStock() {
        Product product1 = new Product(1L, "P1", "D1", 100.0, 1, ProductCategory.WATER_FILTER, 5); // Only 1 in stock
        InvoiceItem item1 = InvoiceItem.builder().productId(1L).quantity(2).build(); // Requesting 2
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L)
                .saleType(SaleType.CASH)
                .items(List.of(item1))
                .build();

        when(productRepository.findById(1L)).thenReturn(Mono.just(product1));

        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Insufficient stock for product ID: 1"))
                .verify();

        verify(salesInvoiceRepository, never()).save(any());
    }

    @Test
    void testCreateSalesInvoice_WithPercentageDiscount() {
        Product product1 = new Product(1L, "P1", "D1", 100.0, 20, ProductCategory.WATER_FILTER, 5);
        InvoiceItem item1 = InvoiceItem.builder().productId(1L).quantity(2).build(); // Subtotal 200
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L)
                .saleType(SaleType.CASH)
                .items(List.of(item1))
                .discountPercentage(0.10) // 10% discount
                .build();

        SalesInvoice savedInvoiceShell = SalesInvoice.builder().id(100L).customerId(1L).saleType(SaleType.CASH)
                .subTotalAmount(200.0).additionalServicesTotalAmount(0.0).discountPercentage(0.10)
                .totalAmount(180.0) // 200 - 20 (10% of 200)
                .build();
        InvoiceItem savedItem1 = InvoiceItem.builder().id(1001L).salesInvoiceId(100L).productId(1L).quantity(2).unitPrice(100.0).subtotal(200.0).productName("P1").build();
        Product product1AfterSale = new Product(1L, "P1", "D1", 100.0, 18, ProductCategory.WATER_FILTER, 5);

        when(productRepository.findById(1L)).thenReturn(Mono.just(product1));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(Mono.just(savedInvoiceShell));
        when(invoiceItemRepository.save(any(InvoiceItem.class))).thenReturn(Mono.just(savedItem1));
        when(productRepository.save(any(Product.class))).thenReturn(Mono.just(product1AfterSale));

        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .assertNext(inv -> {
                    assertEquals(200.0, inv.getSubTotalAmount());
                    assertEquals(0.10, inv.getDiscountPercentage());
                    assertEquals(180.0, inv.getTotalAmount());
                })
                .verifyComplete();
    }

    @Test
    void testCreateSalesInvoice_WithFixedDiscount() {
        Product product1 = new Product(1L, "P1", "D1", 100.0, 20, ProductCategory.WATER_FILTER, 5);
        InvoiceItem item1 = InvoiceItem.builder().productId(1L).quantity(2).build(); // Subtotal 200
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L)
                .saleType(SaleType.CASH)
                .items(List.of(item1))
                .discountAmount(25.0) // 25 fixed discount
                .build();

        SalesInvoice savedInvoiceShell = SalesInvoice.builder().id(100L).customerId(1L).saleType(SaleType.CASH)
                .subTotalAmount(200.0).additionalServicesTotalAmount(0.0).discountAmount(25.0)
                .totalAmount(175.0) // 200 - 25
                .build();
        // ... other mocks similar to percentage discount ...
        InvoiceItem savedItem1 = InvoiceItem.builder().id(1001L).salesInvoiceId(100L).productId(1L).quantity(2).unitPrice(100.0).subtotal(200.0).productName("P1").build();
        Product product1AfterSale = new Product(1L, "P1", "D1", 100.0, 18, ProductCategory.WATER_FILTER, 5);

        when(productRepository.findById(1L)).thenReturn(Mono.just(product1));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(Mono.just(savedInvoiceShell));
        when(invoiceItemRepository.save(any(InvoiceItem.class))).thenReturn(Mono.just(savedItem1));
        when(productRepository.save(any(Product.class))).thenReturn(Mono.just(product1AfterSale));


        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .assertNext(inv -> {
                    assertEquals(200.0, inv.getSubTotalAmount());
                    assertEquals(25.0, inv.getDiscountAmount());
                    assertEquals(175.0, inv.getTotalAmount());
                })
                .verifyComplete();
    }

    @Test
    void testCreateSalesInvoice_WithFixedAdditionalService() {
        Product product1 = new Product(1L, "P1", "D1", 100.0, 20, ProductCategory.WATER_FILTER, 5);
        InvoiceItem item1 = InvoiceItem.builder().productId(1L).quantity(1).build(); // Subtotal 100
        AdditionalServiceItem asi1 = AdditionalServiceItem.builder()
                .serviceName("Installation")
                .costType(AdditionalServiceCostType.FIXED_AMOUNT)
                .value(50.0) // Fixed 50
                .build();
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L).saleType(SaleType.CASH).items(List.of(item1))
                .additionalServices(List.of(asi1))
                .build();

        SalesInvoice savedInvoiceShell = SalesInvoice.builder().id(100L).customerId(1L).saleType(SaleType.CASH)
                .subTotalAmount(100.0).additionalServicesTotalAmount(50.0).totalAmount(150.0).build();
        InvoiceItem savedItem1 = InvoiceItem.builder().id(1001L).salesInvoiceId(100L).productId(1L).quantity(1).unitPrice(100.0).subtotal(100.0).productName("P1").build();
        AdditionalServiceItem savedAsi1 = AdditionalServiceItem.builder().id(2001L).salesInvoiceId(100L).serviceName("Installation").costType(AdditionalServiceCostType.FIXED_AMOUNT).value(50.0).calculatedCost(50.0).build();
        Product product1AfterSale = new Product(1L, "P1", "D1", 100.0, 19, ProductCategory.WATER_FILTER, 5);


        when(productRepository.findById(1L)).thenReturn(Mono.just(product1));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(Mono.just(savedInvoiceShell));
        when(invoiceItemRepository.save(any(InvoiceItem.class))).thenReturn(Mono.just(savedItem1));
        when(additionalServiceItemRepository.save(any(AdditionalServiceItem.class))).thenReturn(Mono.just(savedAsi1));
        when(productRepository.save(any(Product.class))).thenReturn(Mono.just(product1AfterSale));

        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .assertNext(inv -> {
                    assertEquals(100.0, inv.getSubTotalAmount());
                    assertEquals(50.0, inv.getAdditionalServicesTotalAmount());
                    assertEquals(150.0, inv.getTotalAmount());
                    assertEquals(1, inv.getAdditionalServices().size());
                    assertEquals(50.0, inv.getAdditionalServices().get(0).getCalculatedCost());
                })
                .verifyComplete();
    }
    
    @Test
    void testCreateSalesInvoice_WithPercentageAdditionalService_AfterDiscount() {
        Product product1 = new Product(1L, "P1", "D1", 100.0, 20, ProductCategory.WATER_FILTER, 5);
        InvoiceItem item1 = InvoiceItem.builder().productId(1L).quantity(2).build(); // Subtotal 200
        AdditionalServiceItem asi1 = AdditionalServiceItem.builder()
                .serviceName("Support Fee")
                .costType(AdditionalServiceCostType.PERCENTAGE_OF_TOTAL)
                .value(0.05) // 5%
                .build();
        SalesInvoice invoiceInput = SalesInvoice.builder()
                .customerId(1L).saleType(SaleType.CASH).items(List.of(item1))
                .discountAmount(20.0) // Item total after discount: 200 - 20 = 180
                .additionalServices(List.of(asi1)) // 5% of 180 = 9
                .build();

        // Expected: Subtotal=200, Discount=20, AdditionalService=9, Total=189
        SalesInvoice savedInvoiceShell = SalesInvoice.builder().id(100L).customerId(1L).saleType(SaleType.CASH)
                .subTotalAmount(200.0).discountAmount(20.0)
                .additionalServicesTotalAmount(9.0).totalAmount(189.0).build();
        InvoiceItem savedItem1 = InvoiceItem.builder().id(1001L).salesInvoiceId(100L).productId(1L).quantity(2).unitPrice(100.0).subtotal(200.0).productName("P1").build();
        AdditionalServiceItem savedAsi1 = AdditionalServiceItem.builder().id(2001L).salesInvoiceId(100L).serviceName("Support Fee").costType(AdditionalServiceCostType.PERCENTAGE_OF_TOTAL).value(0.05).calculatedCost(9.0).build();
        Product product1AfterSale = new Product(1L, "P1", "D1", 100.0, 18, ProductCategory.WATER_FILTER, 5);

        when(productRepository.findById(1L)).thenReturn(Mono.just(product1));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(Mono.just(savedInvoiceShell));
        when(invoiceItemRepository.save(any(InvoiceItem.class))).thenReturn(Mono.just(savedItem1));
        when(additionalServiceItemRepository.save(any(AdditionalServiceItem.class))).thenReturn(Mono.just(savedAsi1));
        when(productRepository.save(any(Product.class))).thenReturn(Mono.just(product1AfterSale));

        StepVerifier.create(salesService.createSalesInvoice(invoiceInput))
                .assertNext(inv -> {
                    assertEquals(200.0, inv.getSubTotalAmount());
                    assertEquals(20.0, inv.getDiscountAmount());
                    assertEquals(9.0, inv.getAdditionalServicesTotalAmount());
                    assertEquals(189.0, inv.getTotalAmount());
                    assertEquals(1, inv.getAdditionalServices().size());
                    assertEquals(9.0, inv.getAdditionalServices().get(0).getCalculatedCost());
                })
                .verifyComplete();
    }


    @Test
    void testGetAllSalesInvoices() {
        SalesInvoice invoice1 = SalesInvoice.builder().id(1L).customerId(1L).build();
        SalesInvoice invoice2 = SalesInvoice.builder().id(2L).customerId(2L).build();
        when(salesInvoiceRepository.findAll()).thenReturn(Flux.just(invoice1, invoice2));
        when(invoiceItemRepository.findBySalesInvoiceId(anyLong())).thenReturn(Flux.empty());
        when(additionalServiceItemRepository.findBySalesInvoiceId(anyLong())).thenReturn(Flux.empty());

        StepVerifier.create(salesService.getAllSalesInvoices())
                .expectNext(invoice1)
                .expectNext(invoice2)
                .verifyComplete();
    }

    @Test
    void testGetSalesInvoiceById_whenExists() {
        Long invoiceId = 1L;
        SalesInvoice invoice = SalesInvoice.builder().id(invoiceId).customerId(1L).build();
        InvoiceItem item = InvoiceItem.builder().id(10L).salesInvoiceId(invoiceId).productId(1L).build();
        when(salesInvoiceRepository.findById(invoiceId)).thenReturn(Mono.just(invoice));
        when(invoiceItemRepository.findBySalesInvoiceId(invoiceId)).thenReturn(Flux.just(item));
        when(additionalServiceItemRepository.findBySalesInvoiceId(invoiceId)).thenReturn(Flux.empty());

        StepVerifier.create(salesService.getSalesInvoiceById(invoiceId))
                .assertNext(fetchedInvoice -> {
                    assertEquals(invoiceId, fetchedInvoice.getId());
                    assertNotNull(fetchedInvoice.getItems());
                    assertEquals(1, fetchedInvoice.getItems().size());
                    assertEquals(10L, fetchedInvoice.getItems().get(0).getId());
                })
                .verifyComplete();
    }

    @Test
    void testGetSalesInvoiceById_whenNotExists() {
        Long invoiceId = 1L;
        when(salesInvoiceRepository.findById(invoiceId)).thenReturn(Mono.empty());

        StepVerifier.create(salesService.getSalesInvoiceById(invoiceId))
                .verifyComplete(); // Expect no emission

        verify(invoiceItemRepository, never()).findBySalesInvoiceId(anyLong());
    }

    @Test
    void testDeleteSalesInvoice() {
        Long invoiceId = 1L;
        when(salesInvoiceRepository.deleteById(invoiceId)).thenReturn(Mono.empty());

        StepVerifier.create(salesService.deleteSalesInvoice(invoiceId))
                .verifyComplete();
        verify(salesInvoiceRepository).deleteById(invoiceId);
    }
}
