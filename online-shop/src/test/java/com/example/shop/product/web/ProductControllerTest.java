package com.example.shop.product.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.shop.common.config.AppConfig;
import com.example.shop.common.config.ShopProperties;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.product.app.ProductDetails;
import com.example.shop.product.app.ProductService;
import com.example.shop.product.domain.CategoryRef;
import com.example.shop.product.domain.Product;

/**
 * Slice test of the web layer (week 8): only the controller, the JSON conversion, validation and
 * the error handler are started. The service is a Mockito mock, so there is no database here.
 */
@WebMvcTest(ProductController.class)
@Import({ProductApiMapper.class, AppConfig.class})          // the mapper and the Clock for the error handler
@EnableConfigurationProperties(ShopProperties.class)         // currency for the response
class ProductControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    private Product macBook() {
        return Product.restore(1L, "LAP-MBA-13", "MacBook Air 13", "Apple M3", new BigDecimal("649990.00"), 5,
                new CategoryRef(1, "Laptops"), NOW, NOW, 0);
    }

    @Test
    void returnsTheProductAsJson() throws Exception {
        when(productService.findById(1)).thenReturn(macBook());

        mvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("LAP-MBA-13"))
                .andExpect(jsonPath("$.currency").value("KZT"))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.categoryName").value("Laptops"))
                .andExpect(jsonPath("$.version").doesNotExist());   // internal fields never leak
    }

    @Test
    void unknownProductIs404WithTheUnifiedErrorBody() throws Exception {
        when(productService.findById(99)).thenThrow(new ResourceNotFoundException("Product", 99L));

        mvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product with id 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/products/99"));
    }

    @Test
    void createReturns201WithLocationAndTrimsTheName() throws Exception {
        when(productService.create(any(ProductDetails.class))).thenReturn(macBook());

        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "LAP-MBA-13", "name": "  MacBook Air 13  ", "description": "Apple M3",
                                 "price": 649990.00, "stockQuantity": 5, "categoryId": 1}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/1"))
                .andExpect(jsonPath("$.name").value("MacBook Air 13"));

        ArgumentCaptor<ProductDetails> details = ArgumentCaptor.forClass(ProductDetails.class);
        verify(productService).create(details.capture());
        assertThat(details.getValue().name()).isEqualTo("MacBook Air 13");
        assertThat(details.getValue().categoryId()).isEqualTo(1L);
    }

    @Test
    void invalidBodyIs400AndNeverReachesTheService() throws Exception {
        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "a", "name": "", "price": -5, "stockQuantity": 1, "categoryId": 1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid"))
                .andExpect(jsonPath("$.violations.length()").value(3));

        verify(productService, never()).create(any());
    }

    @Test
    void listUsesTheDefaultPageAndReturnsOurPageShape() throws Exception {
        Pageable expected = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("id"));
        when(productService.findAll(eq(null), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(macBook()), expected, 1));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LAP-MBA-13"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
}
