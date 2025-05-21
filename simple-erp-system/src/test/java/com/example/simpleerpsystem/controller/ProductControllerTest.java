package com.example.simpleerpsystem.controller;

import com.example.simpleerpsystem.entity.Product;
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
        Product newProduct = new Product(null, "Laptop", "High-end gaming laptop", 2500.00, 10);

        Product createdProduct = webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newProduct), Product.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Product.class)
                .returnResult().getResponseBody();

        assertNotNull(createdProduct);
        assertNotNull(createdProduct.getId());
        assertEquals(newProduct.getName(), createdProduct.getName());
        assertEquals(newProduct.getDescription(), createdProduct.getDescription());
        assertEquals(newProduct.getPrice(), createdProduct.getPrice());
        assertEquals(newProduct.getQuantityOnHand(), createdProduct.getQuantityOnHand());

        webTestClient.get().uri("/api/v1/products/" + createdProduct.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(fetchedProduct -> {
                    assertEquals(createdProduct.getName(), fetchedProduct.getName());
                    assertEquals(createdProduct.getDescription(), fetchedProduct.getDescription());
                    assertEquals(createdProduct.getPrice(), fetchedProduct.getPrice());
                    assertEquals(createdProduct.getQuantityOnHand(), fetchedProduct.getQuantityOnHand());
                });
    }

    @Test
    void testGetAllProducts_withData() {
        Product product1 = new Product(null, "Keyboard", "Mechanical Keyboard", 150.00, 50);
        Product product2 = new Product(null, "Mouse", "Gaming Mouse", 75.00, 75);

        productRepository.saveAll(List.of(product1, product2)).blockLast();

        webTestClient.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class).hasSize(2)
                .consumeWith(response -> {
                    List<Product> products = response.getResponseBody();
                    assertNotNull(products);
                    assertTrue(products.stream().anyMatch(p -> "Keyboard".equals(p.getName())));
                    assertTrue(products.stream().anyMatch(p -> "Mouse".equals(p.getName())));
                });
    }

    @Test
    void testGetProductById_notFound() {
        webTestClient.get().uri("/api/v1/products/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testUpdateProduct_whenExists() {
        Product initialProduct = new Product(null, "Old Monitor", "24 inch HD", 200.00, 30);
        Product savedProduct = productRepository.save(initialProduct).block();
        assertNotNull(savedProduct);
        assertNotNull(savedProduct.getId());

        Product updatedDetails = new Product(null, "New Monitor", "27 inch 4K", 400.00, 25);

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
                });

        // Optionally, verify persistence
        webTestClient.get().uri("/api/v1/products/" + savedProduct.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .value(product -> {
                    assertEquals(updatedDetails.getName(), product.getName());
                });
    }

    @Test
    void testUpdateProduct_whenNotExists() {
        Product updatedDetails = new Product(null, "NonExistent Product", "Description", 100.00, 10);

        webTestClient.put().uri("/api/v1/products/999")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updatedDetails), Product.class)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testDeleteProduct_whenExists() {
        Product productToDelete = new Product(null, "ToDelete", "Product to be deleted", 50.00, 5);
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
