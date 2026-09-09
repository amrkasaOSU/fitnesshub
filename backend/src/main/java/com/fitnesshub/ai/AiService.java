package com.fitnesshub.ai;

import com.fitnesshub.ai.dto.AiAnswer;
import com.fitnesshub.ai.dto.FitnessContext;
import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.RateLimitExceededException;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The pipeline from spec section 73: auth (handled by Spring Security before
 * we're even called) -> authorization -> data retrieval -> context
 * construction -> provider call -> safety pass -> response. Every step here
 * is deliberately explicit rather than delegated to the AI provider.
 */
@Service
public class AiService {

    private static final List<String> MEDICAL_KEYWORDS = List.of(
            "injury", "injured", "hurt", "pain", "sharp pain", "diagnose", "diagnosis",
            "medication", "medicine", "prescri", "disease", "condition", "doctor", "physical therapist");

    private final AiProvider aiProvider;
    private final FitnessContextService fitnessContextService;
    private final AiRateLimiter rateLimiter;
    private final AuthorizationService authorizationService;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final AIInsightRequestRepository requestRepository;
    private final AIInsightResponseRepository responseRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(AiProvider aiProvider, FitnessContextService fitnessContextService, AiRateLimiter rateLimiter,
                      AuthorizationService authorizationService, CurrentUser currentUser,
                      UserRepository userRepository, AIInsightRequestRepository requestRepository,
                      AIInsightResponseRepository responseRepository, AuditService auditService) {
        this.aiProvider = aiProvider;
        this.fitnessContextService = fitnessContextService;
        this.rateLimiter = rateLimiter;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.responseRepository = responseRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AiAnswer answerFitnessQuestion(UUID requestedClientId, String question) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        User caller = userRepository.findById(currentUser.id()).orElseThrow();

        if (!rateLimiter.tryConsume(caller)) {
            throw new RateLimitExceededException(
                    "You've reached your daily AI question limit (" + rateLimiter.dailyLimitFor(caller) + "/day).");
        }

        FitnessContext context = fitnessContextService.buildContext(clientId, question);
        AiAnswer answer = aiProvider.answerFitnessQuestion(context, question);
        answer = applySafetyPass(answer, question);

        persist(caller.getId(), clientId, question, answer);
        auditService.record(caller.getId(), AuditAction.AI_REQUESTED, "AIInsightRequest", null);
        return answer;
    }

    @Transactional
    public AiAnswer analyzeClientForCoach(UUID clientId) {
        User coach = userRepository.findById(currentUser.id()).orElseThrow();
        authorizationService.assertCoachOwnsClient(coach.getId(), clientId);

        if (!rateLimiter.tryConsume(coach)) {
            throw new RateLimitExceededException(
                    "You've reached your daily AI question limit (" + rateLimiter.dailyLimitFor(coach) + "/day).");
        }

        FitnessContext context = fitnessContextService.buildContext(clientId, null);
        AiAnswer answer = aiProvider.summarizeClientProgress(context);
        answer = applySafetyPass(answer, "");

        persist(coach.getId(), clientId, "[coach client analysis]", answer);
        auditService.record(coach.getId(), AuditAction.AI_REQUESTED, "AIInsightRequest", null);
        return answer;
    }

    /**
     * Belt-and-suspenders on top of the system prompt: if the question itself
     * raises a medical topic, make sure a professional-consultation disclaimer
     * is present even if the model forgot to include one.
     */
    private AiAnswer applySafetyPass(AiAnswer answer, String question) {
        String lower = (question == null ? "" : question).toLowerCase(Locale.ROOT);
        boolean touchesMedical = MEDICAL_KEYWORDS.stream().anyMatch(lower::contains);
        if (!touchesMedical) {
            return answer;
        }
        boolean alreadyWarns = answer.warnings().stream()
                .anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("professional")
                        || w.toLowerCase(Locale.ROOT).contains("doctor"));
        if (alreadyWarns) {
            return answer;
        }
        List<String> warnings = new ArrayList<>(answer.warnings());
        warnings.add("This question touches on pain or a medical topic. FitnessHub's AI assistant is not a "
                + "medical professional - please consult an appropriately qualified healthcare provider.");
        return new AiAnswer(answer.answer(), answer.confidence(), answer.relevantMetrics(),
                answer.recommendation(), answer.reasoningSummary(), warnings);
    }

    private void persist(UUID userId, UUID clientId, String question, AiAnswer answer) {
        try {
            AIInsightRequest request = requestRepository.save(new AIInsightRequest(userId, clientId, question));
            String serialized = objectMapper.writeValueAsString(answer);
            responseRepository.save(new AIInsightResponse(request.getId(), serialized,
                    aiProvider.isConfigured() ? "openai" : "unconfigured"));
        } catch (Exception e) {
            // Never let storage of the AI transcript fail the actual answer the user is waiting on.
        }
    }
}
