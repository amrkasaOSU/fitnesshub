package com.fitnesshub.config.features;

import com.fitnesshub.ai.AiProvider;
import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.subscription.BillingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the UI ask which optional integrations are actually wired up, so it can
 * present them as "coming soon" instead of surfacing a dead end. Derived from
 * the same isConfigured() checks the services themselves use, so the answer
 * can't drift from reality - add a key and the feature turns itself on with no
 * frontend change.
 */
@RestController
@RequestMapping("/api/features")
public class FeatureFlagsController {

    private final AiProvider aiProvider;
    private final BillingService billingService;

    public FeatureFlagsController(AiProvider aiProvider, BillingService billingService) {
        this.aiProvider = aiProvider;
        this.billingService = billingService;
    }

    public record FeatureFlags(boolean aiEnabled, boolean billingEnabled) {
    }

    @GetMapping
    public ApiResponse<FeatureFlags> features() {
        return ApiResponse.of(new FeatureFlags(aiProvider.isConfigured(), billingService.isConfigured()));
    }
}
