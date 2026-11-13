package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.category.ResourceCategoryRequest;
import com.groupthree.Facilitybook.dto.category.ResourceCategoryResponse;
import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.service.ResourceCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resource-categories")
@Tag(name = "Resource categories")
public class ResourceCategoryController {

    private final ResourceCategoryService categoryService;

    public ResourceCategoryController(ResourceCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public PageResponse<ResourceCategoryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return categoryService.list(search, pageable);
    }

    @GetMapping("/{id}")
    public ResourceCategoryResponse get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceCategoryResponse create(@Valid @RequestBody ResourceCategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResourceCategoryResponse update(@PathVariable Long id,
                                           @Valid @RequestBody ResourceCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
