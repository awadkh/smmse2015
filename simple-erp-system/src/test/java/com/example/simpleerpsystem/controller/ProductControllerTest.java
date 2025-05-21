package com.example.simpleerpsystem.controller;

import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.entity.enums.ProductCategory; // New import
import com.example.simpleerpsystem.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class ProductControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll().block(); // Ensure clean state before each test
    }

    @Test
    void testGetAllProducts_empty() {
        webTestClient.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class).hasSize(0);
    }

    @Test
    void testCreateProduct_and_GetById() {
        Product newProduct = new Product(null, "Laptop", "High-end gaming laptop", 2500.00, 10, ProductCategory.AC_UNIT, 5);

        Product createdProduct = webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newProduct), Product.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Product.class)
                .value(product -> { // Renamed for clarity to avoid confusion with outer scope createdProduct
                    assertNotNull(product.getId());
                    assertEquals(newProduct.getName(), product.getName());
                    assertEquals(newProduct.getDescription(), product.getDescription());
                    assertEquals(newProduct.getPrice(), product.getPrice());
                    assertEquals(newProduct.getQuantityOnHand(), product.getQuantityOnHand());
                    assertEquals(newProduct.getCategory(), product.getCategory()); // New assertion
                    assertEquals(newProduct.getLowStockThreshold(), product.getLowStockThreshold()); // New assertion
                })
                .returnResult().getResponseBody(); // Store the returned product with its ID

        assertNotNull(createdProduct); // Ensure createdProduct is not null before using its ID

        webTestClient.get().uri("/api/v1/products/" + createdProduct.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(fetchedProduct -> {
                    assertEquals(createdProduct.getName(), fetchedProduct.getName());
                    assertEquals(createdProduct.getDescription(), fetchedProduct.getDescription());
                    assertEquals(createdProduct.getPrice(), fetchedProduct.getPrice());
                    assertEquals(createdProduct.getQuantityOnHand(), fetchedProduct.getQuantityOnHand());
                    assertEquals(createdProduct.getCategory(), fetchedProduct.getCategory()); // New assertion
                    assertEquals(createdProduct.getLowStockThreshold(), fetchedProduct.getLowStockThreshold()); // New assertion
                });
    }

    @Test
    void testGetAllProducts_withData() {
        Product product1 = new Product(null, "Keyboard", "Mechanical Keyboard", 150.00, 50, ProductCategory.FILTER_PART, 10);
        Product product2 = new Product(null, "Mouse", "Gaming Mouse", 75.00, 75, ProductCategory.SERVICE, 0);

        productRepository.saveAll(List.of(product1, product2)).blockLast();

        webTestClient.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody() // Use generic expectBody for more flexible jsonPath assertions
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[?(@.name == 'Keyboard')].category").isEqualTo(ProductCategory.FILTER_PART.toString())
                .jsonPath("$[?(@.name == 'Keyboard')].lowStockThreshold").isEqualTo(10)
                .jsonPath("$[?(@.name == 'Mouse')].category").isEqualTo(ProductCategory.SERVICE.toString())
                .jsonPath("$[?(@.name == 'Mouse')].lowStockThreshold").isEqualTo(0);
    }

    @Test
    void testGetProductById_notFound() {
        webTestClient.get().uri("/api/v1/products/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testUpdateProduct_whenExists() {
        Product initialProduct = new Product(null, "Old Monitor", "24 inch HD", 200.00, 30, ProductCategory.AC_PART, 5);
        Product savedProduct = productRepository.save(initialProduct).block();
        assertNotNull(savedProduct);
        assertNotNull(savedProduct.getId());

        Product updatedDetails = new Product(null, "New Monitor", "27 inch 4K", 400.00, 25, ProductCategory.WATER_FILTER, 10);

        webTestClient.put().uri("/api/v1/products/" + savedProduct.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updatedDetails), Product.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(product -> {
                    assertEquals(savedProduct.getId(), product.getId());
                    assertEquals(updatedDetails.getName(), product.getName());
                    assertEquals(updatedDetails.getDescription(), product.getDescription());
                    assertEquals(updatedDetails.getPrice(), product.getPrice());
                    assertEquals(updatedDetails.getQuantityOnHand(), product.getQuantityOnHand());
                    assertEquals(updatedDetails.getCategory(), product.getCategory()); // New assertion
                    assertEquals(updatedDetails.getLowStockThreshold(), product.getLowStockThreshold()); // New assertion
                });

        // Optionally, verify persistence
        webTestClient.get().uri("/api/v1/products/" + savedProduct.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(product -> {
                    assertEquals(updatedDetails.getName(), product.getName());
                    assertEquals(updatedDetails.getCategory(), product.getCategory()); // New assertion
                    assertEquals(updatedDetails.getLowStockThreshold(), product.getLowStockThreshold()); // New assertion
                });
    }

    @Test
    void testUpdateProduct_whenNotExists() {
        Product updatedDetails = new Product(null, "NonExistent Product", "Description", 100.00, 10, ProductCategory.SERVICE, 0);

        webTestClient.put().uri("/api/v1/products/999")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updatedDetails), Product.class)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testDeleteProduct_whenExists() {
        Product productToDelete = new Product(null, "ToDelete", "Product to be deleted", 50.00, 5, ProductCategory.AC_PART, 1);
        Product savedProduct = productRepository.save(productToDelete).block();
        assertNotNull(savedProduct);
        assertNotNull(savedProduct.getId());

        webTestClient.delete().uri("/api/v1/products/" + savedProduct.getId())
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/api/v1/products/" + savedProduct.getId())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testDeleteProduct_whenNotExists() {
        webTestClient.delete().uri("/api/v1/products/999")
                .exchange()
                .expectStatus().isNoContent(); // As per current controller/service behavior
    }
}
