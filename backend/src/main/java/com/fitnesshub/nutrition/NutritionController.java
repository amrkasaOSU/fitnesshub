package com.fitnesshub.nutrition;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.common.web.PageResponse;
import com.fitnesshub.nutrition.dto.LogNutritionRequest;
import com.fitnesshub.nutrition.dto.NutritionDashboardDto;
import com.fitnesshub.nutrition.dto.NutritionEntryDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionService nutritionService;

    public NutritionController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NutritionEntryDto>> log(@Valid @RequestBody LogNutritionRequest request) {
        NutritionEntry entry = nutritionService.log(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toDto(entry)));
    }

    @GetMapping
    public ApiResponse<PageResponse<NutritionEntryDto>> history(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<NutritionEntry> result = nutritionService.history(clientId,
                PageRequest.of(page, Math.min(pageSize, 100), Sort.by(Sort.Direction.DESC, "date")));
        return ApiResponse.of(PageResponse.from(result.map(this::toDto)));
    }

    @GetMapping("/dashboard")
    public ApiResponse<NutritionDashboardDto> dashboard(@RequestParam(required = false) UUID clientId) {
        return ApiResponse.of(nutritionService.dashboard(clientId));
    }

    private NutritionEntryDto toDto(NutritionEntry e) {
        return new NutritionEntryDto(e.getId(), e.getDate(), e.getCalories(), e.getProteinGrams(),
                e.getCarbohydratesGrams(), e.getFatGrams(), e.getFiberGrams(), e.getWaterMl(), e.getNotes());
    }
}
