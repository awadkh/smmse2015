package com.example.simpleerpsystem.service;

import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Flux<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Mono<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Mono<Product> createProduct(Product product) {
        // The ID should be null or 0 for creation.
        // R2DBC and H2 with AUTO_INCREMENT should handle ID generation.
        // If the product ID is set, it might lead to an update attempt or error.
        // Forcing it to null ensures a new entity is created.
        product.setId(null);
        return productRepository.save(product);
    }

    public Mono<Product> updateProduct(Long id, Product productDetails) {
        return productRepository.findById(id)
                .flatMap(existingProduct -> {
                    existingProduct.setName(productDetails.getName());
                    existingProduct.setDescription(productDetails.getDescription());
                    existingProduct.setPrice(productDetails.getPrice());
                    existingProduct.setQuantityOnHand(productDetails.getQuantityOnHand());
                    return productRepository.save(existingProduct);
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Product not found with id: " + id)));
    }

    public Mono<Void> deleteProduct(Long id) {
        return productRepository.deleteById(id);
    }
}
