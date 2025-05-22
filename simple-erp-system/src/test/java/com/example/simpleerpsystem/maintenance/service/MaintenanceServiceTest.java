package com.example.simpleerpsystem.maintenance.service;

import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.maintenance.entity.ServiceRecord;
import com.example.simpleerpsystem.maintenance.entity.enums.ServiceType;
import com.example.simpleerpsystem.maintenance.repository.ServiceRecordRepository;
import com.example.simpleerpsystem.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MaintenanceServiceTest {

    @Mock
    private ServiceRecordRepository serviceRecordRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductRepository productRepository;

    private MaintenanceService maintenanceService;

    private ServiceRecord sampleRecord;

    @BeforeEach
    void setUp() {
        maintenanceService = new MaintenanceService(serviceRecordRepository, customerRepository, productRepository);
        sampleRecord = ServiceRecord.builder()
                .id(1L)
                .customerId(1L)
                .productId(10L)
                .serviceType(ServiceType.CLEANING)
                .description("Routine cleaning")
                .serviceDate(LocalDate.now())
                .cost(50.0)
                .isFree(false)
                .notes("Completed")
                .build();
    }

    // createServiceRecord() Tests
    @Test
    void testCreateServiceRecord_Success_Basic_NoProduct() {
        ServiceRecord inputRecord = ServiceRecord.builder()
                .customerId(1L)
                .serviceType(ServiceType.CONSULTATION)
                .serviceDate(LocalDate.now())
                .cost(25.0)
                .isFree(false)
                .description("Initial consultation")
                .build();
        ServiceRecord savedRecord = ServiceRecord.builder().id(2L).customerId(1L).serviceType(ServiceType.CONSULTATION).serviceDate(LocalDate.now()).cost(25.0).isFree(false).description("Initial consultation").build();

        when(customerRepository.existsById(1L)).thenReturn(Mono.just(true));
        // No product ID, so productRepository.existsById should not be called for validation if productId is null
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenReturn(Mono.just(savedRecord));

        StepVerifier.create(maintenanceService.createServiceRecord(inputRecord))
                .assertNext(record -> {
                    assertEquals(2L, record.getId());
                    assertEquals(25.0, record.getCost());
                    assertFalse(record.getIsFree());
                    assertNull(record.getProductId()); // Ensure product ID remains null
                })
                .verifyComplete();

        verify(productRepository, never()).existsById(anyLong()); // Verify product check was skipped
    }

    @Test
    void testCreateServiceRecord_Success_WithProductId() {
        ServiceRecord inputRecord = ServiceRecord.builder().customerId(1L).productId(10L).serviceType(ServiceType.REPAIR_AC_UNIT).serviceDate(LocalDate.now()).cost(150.0).description("AC Repair").build();
        ServiceRecord savedRecord = ServiceRecord.builder().id(3L).customerId(1L).productId(10L).serviceType(ServiceType.REPAIR_AC_UNIT).serviceDate(LocalDate.now()).cost(150.0).isFree(false).description("AC Repair").build();

        when(customerRepository.existsById(1L)).thenReturn(Mono.just(true));
        when(productRepository.existsById(10L)).thenReturn(Mono.just(true));
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenReturn(Mono.just(savedRecord));

        StepVerifier.create(maintenanceService.createServiceRecord(inputRecord))
                .expectNext(savedRecord)
                .verifyComplete();
    }

    @Test
    void testCreateServiceRecord_Success_DefaultingIsFree() {
        // Case 1: cost = 0.0, isFree = null -> isFree should be true
        ServiceRecord inputFree = ServiceRecord.builder().customerId(1L).serviceType(ServiceType.INSPECTION).serviceDate(LocalDate.now()).cost(0.0).isFree(null).build();
        ServiceRecord savedFree = ServiceRecord.builder().id(4L).customerId(1L).serviceType(ServiceType.INSPECTION).serviceDate(LocalDate.now()).cost(0.0).isFree(true).build();

        when(customerRepository.existsById(1L)).thenReturn(Mono.just(true));
        // Ensure save captures the argument to check the defaulted isFree value
        ArgumentCaptor<ServiceRecord> captorFree = ArgumentCaptor.forClass(ServiceRecord.class);
        when(serviceRecordRepository.save(captorFree.capture())).thenReturn(Mono.just(savedFree));

        StepVerifier.create(maintenanceService.createServiceRecord(inputFree))
                .assertNext(record -> assertTrue(record.getIsFree()))
                .verifyComplete();
        assertTrue(captorFree.getValue().getIsFree());

        // Case 2: cost = 50.0, isFree = null -> isFree should be false
        ServiceRecord inputPaid = ServiceRecord.builder().customerId(2L).serviceType(ServiceType.CLEANING).serviceDate(LocalDate.now()).cost(50.0).isFree(null).build();
        ServiceRecord savedPaid = ServiceRecord.builder().id(5L).customerId(2L).serviceType(ServiceType.CLEANING).serviceDate(LocalDate.now()).cost(50.0).isFree(false).build();
        
        when(customerRepository.existsById(2L)).thenReturn(Mono.just(true));
        ArgumentCaptor<ServiceRecord> captorPaid = ArgumentCaptor.forClass(ServiceRecord.class);
        when(serviceRecordRepository.save(captorPaid.capture())).thenReturn(Mono.just(savedPaid));
        
        StepVerifier.create(maintenanceService.createServiceRecord(inputPaid))
                .assertNext(record -> assertFalse(record.getIsFree()))
                .verifyComplete();
        assertFalse(captorPaid.getValue().getIsFree());
    }
    
    @Test
    void testCreateServiceRecord_Success_DefaultingCostAndIsFree() {
        ServiceRecord inputDefaultAll = ServiceRecord.builder().customerId(1L).serviceType(ServiceType.OTHER).serviceDate(LocalDate.now()).cost(null).isFree(null).build();
        // Expect cost to be 0.0 and isFree to be true
        ServiceRecord savedDefaultAll = ServiceRecord.builder().id(6L).customerId(1L).serviceType(ServiceType.OTHER).serviceDate(LocalDate.now()).cost(0.0).isFree(true).build();

        when(customerRepository.existsById(1L)).thenReturn(Mono.just(true));
        ArgumentCaptor<ServiceRecord> captor = ArgumentCaptor.forClass(ServiceRecord.class);
        when(serviceRecordRepository.save(captor.capture())).thenReturn(Mono.just(savedDefaultAll));

        StepVerifier.create(maintenanceService.createServiceRecord(inputDefaultAll))
            .assertNext(record -> {
                assertEquals(0.0, record.getCost());
                assertTrue(record.getIsFree());
            })
            .verifyComplete();
        assertEquals(0.0, captor.getValue().getCost());
        assertTrue(captor.getValue().getIsFree());
    }


    @Test
    void testCreateServiceRecord_Error_CustomerNotFound() {
        ServiceRecord inputRecord = ServiceRecord.builder().customerId(99L).serviceType(ServiceType.OTHER).serviceDate(LocalDate.now()).build();
        when(customerRepository.existsById(99L)).thenReturn(Mono.just(false));

        StepVerifier.create(maintenanceService.createServiceRecord(inputRecord))
                .expectErrorMessage("Customer not found with id: 99")
                .verify();
    }

    @Test
    void testCreateServiceRecord_Error_ProductNotFound() {
        ServiceRecord inputRecord = ServiceRecord.builder().customerId(1L).productId(99L).serviceType(ServiceType.PART_REPLACEMENT).serviceDate(LocalDate.now()).build();
        when(customerRepository.existsById(1L)).thenReturn(Mono.just(true));
        when(productRepository.existsById(99L)).thenReturn(Mono.just(false));

        StepVerifier.create(maintenanceService.createServiceRecord(inputRecord))
                .expectErrorMessage("Product not found with id: 99")
                .verify();
    }

    // updateServiceRecord() Tests
    @Test
    void testUpdateServiceRecord_Success_BasicUpdate() {
        ServiceRecord existingRecord = ServiceRecord.builder().id(1L).customerId(1L).productId(10L).serviceType(ServiceType.CLEANING).description("Old Desc").cost(50.0).isFree(false).serviceDate(LocalDate.now().minusDays(1)).build();
        ServiceRecord updateDetails = ServiceRecord.builder().customerId(1L).productId(10L).serviceType(ServiceType.REPAIR_AC_UNIT).description("New Desc").cost(75.0).isFree(false).serviceDate(LocalDate.now()).build();
        
        when(serviceRecordRepository.findById(1L)).thenReturn(Mono.just(existingRecord));
        // Assuming customerId and productId are not changing, so no existsById calls needed for them
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(maintenanceService.updateServiceRecord(1L, updateDetails))
                .assertNext(record -> {
                    assertEquals("New Desc", record.getDescription());
                    assertEquals(ServiceType.REPAIR_AC_UNIT, record.getServiceType());
                    assertEquals(75.0, record.getCost());
                    assertEquals(LocalDate.now(), record.getServiceDate());
                })
                .verifyComplete();
    }
    
    @Test
    void testUpdateServiceRecord_Success_UpdateIsFreeBasedOnCost() {
        ServiceRecord existingRecord = ServiceRecord.builder().id(1L).customerId(1L).cost(50.0).isFree(false).build();
        ServiceRecord updateDetails = ServiceRecord.builder().customerId(1L).cost(0.0).isFree(null).build(); // isFree will be derived

        when(serviceRecordRepository.findById(1L)).thenReturn(Mono.just(existingRecord));
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        
        StepVerifier.create(maintenanceService.updateServiceRecord(1L, updateDetails))
            .assertNext(record -> {
                assertEquals(0.0, record.getCost());
                assertTrue(record.getIsFree());
            })
            .verifyComplete();
    }
    
    @Test
    void testUpdateServiceRecord_Success_ExplicitlySetIsFree() {
        ServiceRecord existingRecord = ServiceRecord.builder().id(1L).customerId(1L).cost(50.0).isFree(false).build();
        ServiceRecord updateDetails = ServiceRecord.builder().customerId(1L).cost(50.0).isFree(true).build(); // Explicitly set isFree to true

        when(serviceRecordRepository.findById(1L)).thenReturn(Mono.just(existingRecord));
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        
        StepVerifier.create(maintenanceService.updateServiceRecord(1L, updateDetails))
            .assertNext(record -> {
                assertEquals(50.0, record.getCost());
                assertTrue(record.getIsFree());
            })
            .verifyComplete();
    }


    @Test
    void testUpdateServiceRecord_Error_ServiceRecordNotFound() {
        ServiceRecord updateDetails = ServiceRecord.builder().description("Doesn't matter").build();
        when(serviceRecordRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(maintenanceService.updateServiceRecord(99L, updateDetails))
                .expectErrorMessage("ServiceRecord not found with id: 99")
                .verify();
    }

    @Test
    void testUpdateServiceRecord_Error_UpdatedCustomerIdNotFound() {
        ServiceRecord existingRecord = ServiceRecord.builder().id(1L).customerId(1L).build();
        ServiceRecord updateDetails = ServiceRecord.builder().customerId(99L).description("Update with new invalid customer").build();

        when(serviceRecordRepository.findById(1L)).thenReturn(Mono.just(existingRecord));
        when(customerRepository.existsById(99L)).thenReturn(Mono.just(false));

        StepVerifier.create(maintenanceService.updateServiceRecord(1L, updateDetails))
                .expectErrorMessage("New Customer not found with id: 99")
                .verify();
    }

    @Test
    void testUpdateServiceRecord_Error_UpdatedProductIdNotFound() {
        ServiceRecord existingRecord = ServiceRecord.builder().id(1L).customerId(1L).productId(10L).build();
        ServiceRecord updateDetails = ServiceRecord.builder().customerId(1L).productId(99L).description("Update with new invalid product").build();

        when(serviceRecordRepository.findById(1L)).thenReturn(Mono.just(existingRecord));
        when(customerRepository.existsById(1L)).thenReturn(Mono.just(true)); // Original customer is fine
        when(productRepository.existsById(99L)).thenReturn(Mono.just(false));

        StepVerifier.create(maintenanceService.updateServiceRecord(1L, updateDetails))
                .expectErrorMessage("New Product not found with id: 99")
                .verify();
    }

    // Other CRUD and Finder Methods
    @Test
    void testGetServiceRecordById() {
        when(serviceRecordRepository.findById(1L)).thenReturn(Mono.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordById(1L))
                .expectNext(sampleRecord)
                .verifyComplete();
    }

    @Test
    void testGetAllServiceRecords() {
        ServiceRecord record2 = ServiceRecord.builder().id(2L).build();
        when(serviceRecordRepository.findAll()).thenReturn(Flux.just(sampleRecord, record2));
        StepVerifier.create(maintenanceService.getAllServiceRecords())
                .expectNext(sampleRecord)
                .expectNext(record2)
                .verifyComplete();
    }

    @Test
    void testDeleteServiceRecord() {
        when(serviceRecordRepository.deleteById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(maintenanceService.deleteServiceRecord(1L))
                .verifyComplete();
        verify(serviceRecordRepository).deleteById(1L);
    }

    @Test
    void testGetServiceRecordsByCustomerId() {
        when(serviceRecordRepository.findByCustomerId(1L)).thenReturn(Flux.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordsByCustomerId(1L))
                .expectNext(sampleRecord)
                .verifyComplete();
    }
    
    @Test
    void testGetServiceRecordsByProductId() {
        when(serviceRecordRepository.findByProductId(10L)).thenReturn(Flux.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordsByProductId(10L))
                .expectNext(sampleRecord)
                .verifyComplete();
    }

    @Test
    void testGetServiceRecordsByServiceType() {
        when(serviceRecordRepository.findByServiceType(ServiceType.CLEANING)).thenReturn(Flux.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordsByServiceType(ServiceType.CLEANING))
                .expectNext(sampleRecord)
                .verifyComplete();
    }

    @Test
    void testGetServiceRecordsByServiceDate() {
        LocalDate date = LocalDate.now();
        when(serviceRecordRepository.findByServiceDate(date)).thenReturn(Flux.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordsByServiceDate(date))
                .expectNext(sampleRecord)
                .verifyComplete();
    }

    @Test
    void testGetServiceRecordsByServiceDateBetween() {
        LocalDate startDate = LocalDate.now().minusDays(5);
        LocalDate endDate = LocalDate.now();
        when(serviceRecordRepository.findByServiceDateBetween(startDate, endDate)).thenReturn(Flux.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordsByServiceDateBetween(startDate, endDate))
                .expectNext(sampleRecord)
                .verifyComplete();
    }
    
    @Test
    void testGetServiceRecordsByCustomerIdAndServiceDateBetween() {
        Long customerId = 1L;
        LocalDate startDate = LocalDate.now().minusDays(5);
        LocalDate endDate = LocalDate.now();
        when(serviceRecordRepository.findByCustomerIdAndServiceDateBetween(customerId, startDate, endDate)).thenReturn(Flux.just(sampleRecord));
        StepVerifier.create(maintenanceService.getServiceRecordsByCustomerIdAndServiceDateBetween(customerId, startDate, endDate))
                .expectNext(sampleRecord)
                .verifyComplete();
    }

}
