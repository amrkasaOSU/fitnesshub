package com.fitnesshub.subscription;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.subscription.dto.CheckoutSessionDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/checkout")
    public ApiResponse<CheckoutSessionDto> checkout() {
        return ApiResponse.of(billingService.createCheckoutSession());
    }

    @PostMapping("/portal")
    public ApiResponse<CheckoutSessionDto> portal() {
        return ApiResponse.of(billingService.createPortalSession());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(HttpServletRequest request,
                                         @RequestHeader("Stripe-Signature") String signature) throws IOException {
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        billingService.handleWebhook(payload, signature);
        return ResponseEntity.noContent().build();
    }
}
