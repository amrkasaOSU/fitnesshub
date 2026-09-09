package com.fitnesshub.report;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/weight.csv")
    public ResponseEntity<String> weight(@RequestParam(required = false) UUID clientId) {
        return csv(exportService.exportWeightCsv(clientId), "weight-history.csv");
    }

    @GetMapping("/steps.csv")
    public ResponseEntity<String> steps(@RequestParam(required = false) UUID clientId) {
        return csv(exportService.exportStepsCsv(clientId), "step-history.csv");
    }

    @GetMapping("/nutrition.csv")
    public ResponseEntity<String> nutrition(@RequestParam(required = false) UUID clientId) {
        return csv(exportService.exportNutritionCsv(clientId), "nutrition-history.csv");
    }

    @GetMapping("/workouts.csv")
    public ResponseEntity<String> workouts(@RequestParam(required = false) UUID clientId) {
        return csv(exportService.exportWorkoutsCsv(clientId), "workout-history.csv");
    }

    private ResponseEntity<String> csv(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
