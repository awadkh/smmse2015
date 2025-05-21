package com.example.simpleerpsystem.service;

import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.entity.enums.ProductCategory; // New import
import com.example.simpleerpsystem.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void testGetAllProducts() {
        Product product1 = new Product(1L, "Test Product 1", "Desc 1", 10.0, 100, ProductCategory.WATER_FILTER, 10);
        Product product2 = new Product(2L, "Test Product 2", "Desc 2", 20.0, 200, ProductCategory.AC_PART, 20);
        when(productRepository.findAll()).thenReturn(Flux.just(product1, product2));

        StepVerifier.create(productService.getAllProducts())
                .expectNext(product1)
                .expectNext(product2)
                .verifyComplete();
    }

    @Test
    void testGetProductById_whenExists() {
        Product product = new Product(1L, "Test Product", "Desc", 10.0, 100, ProductCategory.SERVICE, 0);
        when(productRepository.findById(1L)).thenReturn(Mono.just(product));

        StepVerifier.create(productService.getProductById(1L))
                .expectNext(product)
                .verifyComplete();
    }

    @Test
    void testGetProductById_whenNotExists() {
        when(productRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(productService.getProductById(1L))
                .verifyComplete();
    }

    @Test
    void testCreateProduct() {
        Product productToSave = new Product(null, "New Product", "New Desc", 30.0, 50, ProductCategory.AC_UNIT, 5);
        Product savedProduct = new Product(1L, "New Product", "New Desc", 30.0, 50, ProductCategory.AC_UNIT, 5);
        // Ensure the product passed to save has a null ID, as per service logic.
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            if (p.getId() == null) { // Simulating DB generating ID
                return Mono.just(new Product(1L, p.getName(), p.getDescription(), p.getPrice(), p.getQuantityOnHand(), p.getCategory(), p.getLowStockThreshold()));
            }
            return Mono.just(p); // Should not happen if service logic is correct
        });


        StepVerifier.create(productService.createProduct(productToSave))
                .expectNextMatches(p -> p.getId() != null && p.getId().equals(1L) &&
                                        p.getName().equals("New Product") &&
                                        p.getCategory() == ProductCategory.AC_UNIT &&
                                        p.getLowStockThreshold().equals(5))
                .verifyComplete();
    }


    @Test
    void testUpdateProduct_whenExists() {
        Long productId = 1L;
        Product existingProduct = new Product(productId, "Old Name", "Old Desc", 10.0, 10, ProductCategory.FILTER_PART, 2);
        Product productDetails = new Product(null, "Updated Name", "Updated Desc", 12.0, 12, ProductCategory.WATER_FILTER, 3); // ID in details is ignored
        Product updatedProductInstance = new Product(productId, "Updated Name", "Updated Desc", 12.0, 12, ProductCategory.WATER_FILTER, 3);

        when(productRepository.findById(productId)).thenReturn(Mono.just(existingProduct));
        when(productRepository.save(any(Product.class))).thenReturn(Mono.just(updatedProductInstance));

        StepVerifier.create(productService.updateProduct(productId, productDetails))
                .expectNextMatches(p -> p.getName().equals("Updated Name") &&
                                        p.getCategory() == ProductCategory.WATER_FILTER &&
                                        p.getLowStockThreshold().equals(3))
                .verifyComplete();
    }

    @Test
    void testUpdateProduct_whenNotExists() {
        Long productId = 1L;
        Product productDetails = new Product(null, "NonExistent Update", "Desc", 1.0, 1, ProductCategory.SERVICE, 0);
        when(productRepository.findById(productId)).thenReturn(Mono.empty());

        StepVerifier.create(productService.updateProduct(productId, productDetails))
                .expectErrorMessage("Product not found with id: " + productId)
                .verify();
    }

    @Test
    void testDeleteProduct() {
        Long productId = 1L;
        when(productRepository.deleteById(productId)).thenReturn(Mono.empty()); // Mono<Void>

        StepVerifier.create(productService.deleteProduct(productId))
                .verifyComplete();

        verify(productRepository).deleteById(productId);
    }
}
