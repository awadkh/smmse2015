package com.example.simpleerpsystem.supplier.service;

import com.example.simpleerpsystem.supplier.entity.Supplier;
import com.example.simpleerpsystem.supplier.entity.SupplierContactNumber;
import com.example.simpleerpsystem.supplier.repository.SupplierContactNumberRepository;
import com.example.simpleerpsystem.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierContactNumberRepository supplierContactNumberRepository;

    private SupplierService supplierService;

    private Supplier sampleSupplier;
    private SupplierContactNumber sampleContactNumber;

    @BeforeEach
    void setUp() {
        supplierService = new SupplierService(supplierRepository, supplierContactNumberRepository);

        sampleSupplier = Supplier.builder().id(1L).name("Test Supplier").email("test@supplier.com").accountBalance(100.0).build();
        sampleContactNumber = SupplierContactNumber.builder().id(10L).supplierId(1L).phoneNumber("123-456-7890").build();
        sampleSupplier.setContactNumbers(List.of(sampleContactNumber)); // Manually set for some tests
    }

    // createSupplier() Tests
    @Test
    void testCreateSupplier_Success() {
        Supplier inputSupplier = Supplier.builder().name("New Supplier").email("new@supplier.com")
            .contactNumbers(List.of(SupplierContactNumber.builder().phoneNumber("123").build()))
            .build(); // accountBalance is null, will be defaulted
        Supplier savedSupplierShell = Supplier.builder().id(1L).name("New Supplier").email("new@supplier.com").accountBalance(0.0).build();
        SupplierContactNumber savedContact = SupplierContactNumber.builder().id(10L).supplierId(1L).phoneNumber("123").build();

        when(supplierRepository.findByName("New Supplier")).thenReturn(Mono.empty());
        when(supplierRepository.findByEmail("new@supplier.com")).thenReturn(Mono.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            // Simulate the save operation by returning the shell but with values from the input
            Supplier s = invocation.getArgument(0);
            // The shell has the ID and potentially other DB-set defaults
            savedSupplierShell.setName(s.getName());
            savedSupplierShell.setEmail(s.getEmail());
            savedSupplierShell.setContactPerson(s.getContactPerson());
            // ... copy other fields if necessary ...
            return Mono.just(savedSupplierShell);
        });
        when(supplierContactNumberRepository.save(any(SupplierContactNumber.class))).thenReturn(Mono.just(savedContact));

        StepVerifier.create(supplierService.createSupplier(inputSupplier))
            .assertNext(s -> {
                assertEquals(1L, s.getId());
                assertEquals(0.0, s.getAccountBalance()); // Defaulted
                assertNotNull(s.getContactNumbers());
                assertEquals(1, s.getContactNumbers().size());
                assertEquals(10L, s.getContactNumbers().get(0).getId());
                assertEquals(1L, s.getContactNumbers().get(0).getSupplierId());
            })
            .verifyComplete();

        ArgumentCaptor<Supplier> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(supplierCaptor.capture());
        assertNull(supplierCaptor.getValue().getId()); // ID should be null before save

        ArgumentCaptor<SupplierContactNumber> contactCaptor = ArgumentCaptor.forClass(SupplierContactNumber.class);
        verify(supplierContactNumberRepository).save(contactCaptor.capture());
        assertNull(contactCaptor.getValue().getId()); // ID should be null before save
        assertEquals(1L, contactCaptor.getValue().getSupplierId()); // SupplierId should be set
    }

    @Test
    void testCreateSupplier_Error_NameAlreadyExists() {
        Supplier inputSupplier = Supplier.builder().name("Existing Name").email("unique@supplier.com").build();
        Supplier existingSupplier = Supplier.builder().id(2L).name("Existing Name").build();

        when(supplierRepository.findByName("Existing Name")).thenReturn(Mono.just(existingSupplier));
        // findByEmail should not be called if name check fails first

        StepVerifier.create(supplierService.createSupplier(inputSupplier))
                .expectErrorMessage("Supplier name already exists: Existing Name")
                .verify();

        verify(supplierRepository, never()).save(any());
    }

    @Test
    void testCreateSupplier_Error_EmailAlreadyExists() {
        Supplier inputSupplier = Supplier.builder().name("Unique Name").email("existing@supplier.com").build();
        Supplier existingSupplierWithEmail = Supplier.builder().id(3L).email("existing@supplier.com").build();

        when(supplierRepository.findByName("Unique Name")).thenReturn(Mono.empty());
        when(supplierRepository.findByEmail("existing@supplier.com")).thenReturn(Mono.just(existingSupplierWithEmail));

        StepVerifier.create(supplierService.createSupplier(inputSupplier))
                .expectErrorMessage("Supplier email already exists: existing@supplier.com")
                .verify();
        
        verify(supplierRepository, never()).save(any());
    }

    // updateSupplier() Tests
    @Test
    void testUpdateSupplier_Success_UpdateAllFieldsAndContactNumbers() {
        Long supplierId = 1L;
        Supplier existingSupplier = Supplier.builder().id(supplierId).name("Old Name").email("old@supplier.com").accountBalance(50.0)
            .contactNumbers(List.of(SupplierContactNumber.builder().id(10L).supplierId(supplierId).phoneNumber("old-123").build()))
            .build();
        
        SupplierContactNumber newContact1 = SupplierContactNumber.builder().phoneNumber("new-456").build();
        SupplierContactNumber newContact2 = SupplierContactNumber.builder().phoneNumber("new-789").build();
        Supplier supplierDetails = Supplier.builder().name("New Name").email("new@supplier.com").accountBalance(100.0)
            .contactNumbers(List.of(newContact1, newContact2))
            .contactPerson("New Person")
            .address("New Address")
            .suppliedItemsDescription("New Items")
            .build();

        Supplier updatedSupplierShell = Supplier.builder().id(supplierId).name("New Name").email("new@supplier.com").accountBalance(100.0)
            .contactPerson("New Person").address("New Address").suppliedItemsDescription("New Items").build();

        SupplierContactNumber savedNewContact1 = SupplierContactNumber.builder().id(11L).supplierId(supplierId).phoneNumber("new-456").build();
        SupplierContactNumber savedNewContact2 = SupplierContactNumber.builder().id(12L).supplierId(supplierId).phoneNumber("new-789").build();

        when(supplierRepository.findById(supplierId)).thenReturn(Mono.just(existingSupplier));
        when(supplierRepository.findByName("New Name")).thenReturn(Mono.empty()); // New name is unique
        when(supplierRepository.findByEmail("new@supplier.com")).thenReturn(Mono.empty()); // New email is unique
        when(supplierRepository.save(any(Supplier.class))).thenReturn(Mono.just(updatedSupplierShell));
        
        when(supplierContactNumberRepository.findBySupplierId(supplierId)).thenReturn(Flux.fromIterable(existingSupplier.getContactNumbers()));
        when(supplierContactNumberRepository.deleteAll(anyList())).thenReturn(Mono.empty());
        
        // Mock save for each new contact number
        AtomicLong contactIdCounter = new AtomicLong(11L);
        when(supplierContactNumberRepository.save(any(SupplierContactNumber.class))).thenAnswer(invocation -> {
            SupplierContactNumber cn = invocation.getArgument(0);
            cn.setId(contactIdCounter.getAndIncrement());
            cn.setSupplierId(supplierId);
            return Mono.just(cn);
        });


        StepVerifier.create(supplierService.updateSupplier(supplierId, supplierDetails))
            .assertNext(s -> {
                assertEquals("New Name", s.getName());
                assertEquals("new@supplier.com", s.getEmail());
                assertEquals(100.0, s.getAccountBalance());
                assertEquals("New Person", s.getContactPerson());
                assertEquals("New Address", s.getAddress());
                assertEquals("New Items", s.getSuppliedItemsDescription());
                assertNotNull(s.getContactNumbers());
                assertEquals(2, s.getContactNumbers().size());
                assertTrue(s.getContactNumbers().stream().anyMatch(cn -> "new-456".equals(cn.getPhoneNumber()) && cn.getId().equals(11L)));
                assertTrue(s.getContactNumbers().stream().anyMatch(cn -> "new-789".equals(cn.getPhoneNumber()) && cn.getId().equals(12L)));
            })
            .verifyComplete();

        verify(supplierContactNumberRepository).deleteAll(existingSupplier.getContactNumbers());
        verify(supplierContactNumberRepository, times(2)).save(any(SupplierContactNumber.class));
    }
    
    @Test
    void testUpdateSupplier_Success_NoContactNumberChanges() {
        Long supplierId = 1L;
        Supplier existingSupplier = Supplier.builder().id(supplierId).name("Old Name").email("old@supplier.com")
            .contactNumbers(Collections.emptyList()) // No existing contacts
            .build();
        Supplier supplierDetails = Supplier.builder().name("New Name").email("new@supplier.com")
            .contactNumbers(Collections.emptyList()) // No new contacts either
            .build();
        Supplier updatedSupplierShell = Supplier.builder().id(supplierId).name("New Name").email("new@supplier.com").build();

        when(supplierRepository.findById(supplierId)).thenReturn(Mono.just(existingSupplier));
        when(supplierRepository.findByName("New Name")).thenReturn(Mono.empty());
        when(supplierRepository.findByEmail("new@supplier.com")).thenReturn(Mono.empty());
        when(supplierRepository.save(any(Supplier.class))).thenReturn(Mono.just(updatedSupplierShell));
        when(supplierContactNumberRepository.findBySupplierId(supplierId)).thenReturn(Flux.empty()); // For existing contacts
        // deleteAll should not be called if existing is empty

        StepVerifier.create(supplierService.updateSupplier(supplierId, supplierDetails))
            .assertNext(s -> {
                assertEquals("New Name", s.getName());
                assertTrue(s.getContactNumbers() == null || s.getContactNumbers().isEmpty());
            })
            .verifyComplete();
        
        verify(supplierContactNumberRepository, never()).deleteAll(anyList());
        verify(supplierContactNumberRepository, never()).save(any(SupplierContactNumber.class));
    }


    @Test
    void testUpdateSupplier_Error_SupplierNotFound() {
        Long supplierId = 99L;
        Supplier supplierDetails = Supplier.builder().name("Any Name").build();
        when(supplierRepository.findById(supplierId)).thenReturn(Mono.empty());

        StepVerifier.create(supplierService.updateSupplier(supplierId, supplierDetails))
                .expectErrorMessage("Supplier not found with id: " + supplierId)
                .verify();
    }

    @Test
    void testUpdateSupplier_Error_UpdatedNameNotUnique() {
        Long supplierIdToUpdate = 1L;
        Supplier existingSupplier = Supplier.builder().id(supplierIdToUpdate).name("Original Name").build();
        Supplier supplierWithNameConflict = Supplier.builder().id(2L).name("Conflicting Name").build(); // Different ID
        Supplier supplierDetails = Supplier.builder().name("Conflicting Name").build();

        when(supplierRepository.findById(supplierIdToUpdate)).thenReturn(Mono.just(existingSupplier));
        when(supplierRepository.findByName("Conflicting Name")).thenReturn(Mono.just(supplierWithNameConflict));

        StepVerifier.create(supplierService.updateSupplier(supplierIdToUpdate, supplierDetails))
                .expectErrorMessage("Supplier name already exists: Conflicting Name")
                .verify();
    }
    
    @Test
    void testUpdateSupplier_Error_UpdatedEmailNotUnique() {
        Long supplierIdToUpdate = 1L;
        Supplier existingSupplier = Supplier.builder().id(supplierIdToUpdate).name("Original Name").email("original@example.com").build();
        Supplier supplierWithEmailConflict = Supplier.builder().id(2L).name("Other Name").email("conflicting@example.com").build();
        Supplier supplierDetails = Supplier.builder().name("Original Name").email("conflicting@example.com").build();

        when(supplierRepository.findById(supplierIdToUpdate)).thenReturn(Mono.just(existingSupplier));
        // Name is not changed, so findByName for "Original Name" might return existingSupplier or empty, doesn't matter for this test path
        when(supplierRepository.findByEmail("conflicting@example.com")).thenReturn(Mono.just(supplierWithEmailConflict));

        StepVerifier.create(supplierService.updateSupplier(supplierIdToUpdate, supplierDetails))
                .expectErrorMessage("Supplier email already exists: conflicting@example.com")
                .verify();
    }


    // getSupplierById(), getAllSuppliers(), deleteSupplier() Tests
    @Test
    void testGetSupplierById_Success() {
        when(supplierRepository.findById(1L)).thenReturn(Mono.just(sampleSupplier));
        when(supplierContactNumberRepository.findBySupplierId(1L)).thenReturn(Flux.just(sampleContactNumber));

        StepVerifier.create(supplierService.getSupplierById(1L))
                .assertNext(s -> {
                    assertEquals(1L, s.getId());
                    assertNotNull(s.getContactNumbers());
                    assertEquals(1, s.getContactNumbers().size());
                    assertEquals("123-456-7890", s.getContactNumbers().get(0).getPhoneNumber());
                })
                .verifyComplete();
    }
    
    @Test
    void testGetSupplierById_NotFound() {
        when(supplierRepository.findById(99L)).thenReturn(Mono.empty());
        // No need to mock supplierContactNumberRepository as it won't be called
        
        StepVerifier.create(supplierService.getSupplierById(99L))
                .expectErrorMessage("Supplier not found with id: 99")
                .verify();
    }


    @Test
    void testGetAllSuppliers() {
        Supplier supplier2 = Supplier.builder().id(2L).name("Supplier Two").build();
        SupplierContactNumber contact2 = SupplierContactNumber.builder().id(11L).supplierId(2L).phoneNumber("987-654-3210").build();

        when(supplierRepository.findAll()).thenReturn(Flux.just(sampleSupplier, supplier2));
        when(supplierContactNumberRepository.findBySupplierId(1L)).thenReturn(Flux.just(sampleContactNumber));
        when(supplierContactNumberRepository.findBySupplierId(2L)).thenReturn(Flux.just(contact2));

        StepVerifier.create(supplierService.getAllSuppliers())
                .expectNextMatches(s -> s.getId().equals(1L) && s.getContactNumbers().size() == 1)
                .expectNextMatches(s -> s.getId().equals(2L) && s.getContactNumbers().size() == 1)
                .verifyComplete();
    }

    @Test
    void testDeleteSupplier() {
        Long supplierId = 1L;
        when(supplierRepository.deleteById(supplierId)).thenReturn(Mono.empty());

        StepVerifier.create(supplierService.deleteSupplier(supplierId))
                .verifyComplete();
        verify(supplierRepository).deleteById(supplierId);
    }
}
