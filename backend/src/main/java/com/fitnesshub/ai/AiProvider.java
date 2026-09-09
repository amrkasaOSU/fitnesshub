package com.fitnesshub.ai;

import com.fitnesshub.ai.dto.AiAnswer;
import com.fitnesshub.ai.dto.FitnessContext;

/**
 * Everything FitnessHub needs from an LLM backend. The rest of the app never
 * talks to OpenAI (or any provider) directly, so swapping providers means
 * writing a new implementation of this interface, not touching AiService.
 */
public interface AiProvider {

    AiAnswer answerFitnessQuestion(FitnessContext context, String question);

    AiAnswer analyzeExerciseProgress(FitnessContext context);

    AiAnswer summarizeClientProgress(FitnessContext context);

    boolean isConfigured();
}
