package com.fitnesshub.ai;

import com.fitnesshub.ai.dto.AiAnswer;
import com.fitnesshub.ai.dto.ClientAnalysisRequest;
import com.fitnesshub.ai.dto.FitnessQuestionRequest;
import com.fitnesshub.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/fitness-question")
    public ApiResponse<AiAnswer> fitnessQuestion(@Valid @RequestBody FitnessQuestionRequest request) {
        return ApiResponse.of(aiService.answerFitnessQuestion(request.clientId(), request.question()));
    }

    @PostMapping("/client-analysis")
    @PreAuthorize("hasRole('COACH')")
    public ApiResponse<AiAnswer> clientAnalysis(@Valid @RequestBody ClientAnalysisRequest request) {
        return ApiResponse.of(aiService.analyzeClientForCoach(request.clientId()));
    }
}
