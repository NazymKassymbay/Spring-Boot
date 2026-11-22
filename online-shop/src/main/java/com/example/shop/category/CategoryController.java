package com.example.shop.category;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.shop.common.web.ApiAdminOnly;
import com.example.shop.common.web.ErrorResponse;

/**
 * Web layer of a CRUD context: HTTP in, service call, DTO out. It never touches the repository.
 *
 * <p>SIS 3 (lecture week 10, slides 9-10): springdoc infers paths, parameters and DTO schemas by itself; the
 * annotations add what it cannot know — what each operation means and which error statuses really happen
 * on it, each with the shared {@link ErrorResponse} body.
 */
@Tag(name = "Categories", description = "Product categories (cached list)")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "List all categories with their product count (served from the cache)")
    @ApiResponse(responseCode = "200", description = "All categories")
    @GetMapping
    public List<CategoryResponse> findAll() {
        return categoryService.findAll();
    }

    @Operation(summary = "Get one category by id")
    @ApiResponse(responseCode = "200", description = "The category")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public CategoryResponse findById(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    // 201 Created + Location header pointing at the new resource
    @Operation(summary = "Create a category")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new category")
    @ApiResponse(responseCode = "400", description = "Invalid body; the violations list names each bad field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A category with this name already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Replace a category's name and description")
    @ApiResponse(responseCode = "200", description = "The updated category")
    @ApiResponse(responseCode = "400", description = "Invalid body or id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The new name is taken, or the category was changed concurrently",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @Operation(summary = "Delete an empty category")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No category with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The category still has products",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
