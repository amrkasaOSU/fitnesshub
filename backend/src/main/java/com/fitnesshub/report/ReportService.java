package com.fitnesshub.report;

import com.fitnesshub.analytics.AdherenceService;
import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.bodyweight.WeightService;
import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.coach.CoachNoteRepository;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.goal.GoalRepository;
import com.fitnesshub.goal.GoalService;
import com.fitnesshub.goal.GoalStatus;
import com.fitnesshub.nutrition.NutritionEntryRepository;
import com.fitnesshub.progress.PersonalRecordService;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.StepEntryRepository;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Generates the client progress report PDF (spec sections 116-117). Coach
 * private notes are only included when explicitly requested by the caller
 * (the coach), never by default.
 */
@Service
public class ReportService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final WeightService weightService;
    private final AdherenceService adherenceService;
    private final PersonalRecordService personalRecordService;
    private final GoalRepository goalRepository;
    private final GoalService goalService;
    private final StepEntryRepository stepEntryRepository;
    private final NutritionEntryRepository nutritionEntryRepository;
    private final CoachNoteRepository coachNoteRepository;
    private final AuthorizationService authorizationService;
    private final CurrentUser currentUser;

    public ReportService(UserRepository userRepository, ClientProfileRepository clientProfileRepository,
                          WeightService weightService, AdherenceService adherenceService,
                          PersonalRecordService personalRecordService, GoalRepository goalRepository,
                          GoalService goalService, StepEntryRepository stepEntryRepository,
                          NutritionEntryRepository nutritionEntryRepository,
                          CoachNoteRepository coachNoteRepository, AuthorizationService authorizationService,
                          CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.weightService = weightService;
        this.adherenceService = adherenceService;
        this.personalRecordService = personalRecordService;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
        this.stepEntryRepository = stepEntryRepository;
        this.nutritionEntryRepository = nutritionEntryRepository;
        this.coachNoteRepository = coachNoteRepository;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public byte[] generateProgressReport(UUID clientId, LocalDate from, LocalDate to, boolean includeCoachNotesRequested) {
        authorizationService.assertCanAccessClient(clientId);
        boolean includeCoachNotes = includeCoachNotesRequested && currentUser.isCoach();
        User client = userRepository.findById(clientId).orElseThrow(() -> NotFoundException.of("User", clientId));
        ZoneId zone = ZoneId.of(client.getTimezone());

        WeightDashboardDto weight = weightService.dashboard(clientId);
        AdherenceResult adherence = adherenceService.calculateForClient(clientId, from, to, zone);
        var recentPrs = personalRecordService.recentForClient(clientId, 8);
        var goals = goalRepository.findByClientIdAndStatus(clientId, GoalStatus.ACTIVE).stream()
                .map(goalService::toDto).toList();

        var steps = stepEntryRepository.findByClientIdAndDateBetweenOrderByDateAsc(clientId, from, to);
        double avgSteps = steps.stream().mapToInt(s -> s.getSteps()).average().orElse(0);

        var nutrition = nutritionEntryRepository.findByClientIdAndDateBetweenOrderByDateAsc(clientId, from, to);
        double avgCalories = nutrition.stream().mapToDouble(n -> n.getCalories().doubleValue()).average().orElse(0);
        double avgProtein = nutrition.stream().mapToDouble(n -> n.getProteinGrams().doubleValue()).average().orElse(0);

        List<String> coachNotes = includeCoachNotes
                ? coachNoteRepository.findByCoachIdAndClientIdOrderByCreatedAtDesc(
                        clientProfileRepository.findByUserId(clientId).map(p -> p.getCoachId()).orElse(null), clientId)
                        .stream().map(n -> n.getContent()).toList()
                : List.of();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;
                float lineHeight = 16;

                y = writeLine(cs, bold, 20, margin, y, "FitnessHub");
                y = writeLine(cs, regular, 11, margin, y - 4, "Client Progress Report");
                y -= 10;
                y = writeLine(cs, regular, 10, margin, y,
                        client.getFirstName() + " " + client.getLastName() + "  |  " + from + " to " + to);
                y -= 14;

                y = writeSectionHeader(cs, bold, margin, y, "Body Weight");
                y = writeLine(cs, regular, 10, margin, y,
                        "Current: " + orNa(weight.current()) + "   Starting: " + orNa(weight.starting())
                                + "   Total change: " + orNa(weight.totalChange()));
                y -= 8;

                y = writeSectionHeader(cs, bold, margin, y, "Training Adherence");
                y = writeLine(cs, regular, 10, margin, y,
                        adherence.plannedWorkouts() == 0 ? "No active program in this range."
                                : adherence.completedWorkouts() + " / " + adherence.plannedWorkouts()
                                + " planned workouts completed (" + adherence.adherencePercentage() + "%)");
                y -= 8;

                y = writeSectionHeader(cs, bold, margin, y, "Nutrition & Steps (period average)");
                y = writeLine(cs, regular, 10, margin, y,
                        String.format("Calories: %.0f    Protein: %.0fg    Steps: %.0f", avgCalories, avgProtein, avgSteps));
                y -= 8;

                y = writeSectionHeader(cs, bold, margin, y, "Recent Personal Records");
                if (recentPrs.isEmpty()) {
                    y = writeLine(cs, regular, 10, margin, y, "No personal records in this period.");
                } else {
                    for (var pr : recentPrs) {
                        y = writeLine(cs, regular, 10, margin, y,
                                "- " + pr.exerciseName() + ": " + pr.recordType() + " = " + pr.value());
                    }
                }
                y -= 8;

                y = writeSectionHeader(cs, bold, margin, y, "Active Goals");
                if (goals.isEmpty()) {
                    y = writeLine(cs, regular, 10, margin, y, "No active goals.");
                } else {
                    for (var g : goals) {
                        y = writeLine(cs, regular, 10, margin, y,
                                "- " + g.name() + ": " + g.currentValue() + " / " + g.targetValue() + " " + g.unit()
                                        + (g.percentComplete() == null ? "" : " (" + g.percentComplete() + "%)"));
                    }
                }

                if (includeCoachNotes && !coachNotes.isEmpty()) {
                    y -= 8;
                    y = writeSectionHeader(cs, bold, margin, y, "Coach Notes");
                    for (String note : coachNotes) {
                        y = writeLine(cs, regular, 10, margin, y, "- " + note);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private float writeSectionHeader(PDPageContentStream cs, PDFont font, float x, float y, String text) throws IOException {
        return writeLine(cs, font, 13, x, y, text);
    }

    private float writeLine(PDPageContentStream cs, PDFont font, int size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text == null ? "" : text);
        cs.endText();
        return y - (size + 6);
    }

    private String orNa(Object value) {
        return value == null ? "n/a" : value.toString();
    }
}
