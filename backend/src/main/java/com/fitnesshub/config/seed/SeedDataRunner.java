package com.fitnesshub.config.seed;

import com.fitnesshub.client.ActivityLevel;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.client.FitnessGoal;
import com.fitnesshub.client.Sex;
import com.fitnesshub.coach.CoachClient;
import com.fitnesshub.coach.CoachClientRepository;
import com.fitnesshub.coach.CoachProfile;
import com.fitnesshub.coach.CoachProfileRepository;
import com.fitnesshub.bodyweight.WeightEntry;
import com.fitnesshub.bodyweight.WeightEntryRepository;
import com.fitnesshub.exercise.Equipment;
import com.fitnesshub.exercise.Exercise;
import com.fitnesshub.exercise.ExerciseRepository;
import com.fitnesshub.exercise.MovementPattern;
import com.fitnesshub.exercise.MuscleGroup;
import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.goal.CheckInRepository;
import com.fitnesshub.goal.Goal;
import com.fitnesshub.goal.GoalRepository;
import com.fitnesshub.goal.GoalType;
import com.fitnesshub.messaging.Conversation;
import com.fitnesshub.messaging.ConversationRepository;
import com.fitnesshub.messaging.Message;
import com.fitnesshub.messaging.MessageRepository;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.nutrition.NutritionEntryRepository;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.program.ClientProgramRepository;
import com.fitnesshub.program.Program;
import com.fitnesshub.program.ProgramDay;
import com.fitnesshub.program.ProgramDayRepository;
import com.fitnesshub.program.ProgramExercise;
import com.fitnesshub.program.ProgramExerciseRepository;
import com.fitnesshub.program.ProgramRepository;
import com.fitnesshub.progress.PersonalRecordService;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.steps.StepEntryRepository;
import com.fitnesshub.steps.StepSource;
import com.fitnesshub.user.Role;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutExercise;
import com.fitnesshub.workout.WorkoutExerciseRepository;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSessionStatus;
import com.fitnesshub.workout.WorkoutSet;
import com.fitnesshub.workout.WorkoutSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Populates a fresh database with a realistic demo coach, five clients each
 * telling a different coaching story (spec sections 142-146), an exercise
 * library, two programs, and enough workout/weight/nutrition/step/check-in
 * history to make every dashboard, chart, and attention flag mean something.
 * Only runs once, against an empty database (see run()).
 */
@Component
@ConditionalOnProperty(name = "fitnesshub.seed.enabled", havingValue = "true", matchIfMissing = true)
// Hard backstop independent of the flag above: these demo accounts use a
// password published in the public README, so they must never be creatable on
// a production instance - not even by a misconfigured environment variable.
@Profile("!prod")
public class SeedDataRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private static final String DEMO_PASSWORD = "FitnessHub!2024";

    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachClientRepository coachClientRepository;
    private final ExerciseRepository exerciseRepository;
    private final ProgramRepository programRepository;
    private final ProgramDayRepository programDayRepository;
    private final ProgramExerciseRepository programExerciseRepository;
    private final ClientProgramRepository clientProgramRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final StepEntryRepository stepEntryRepository;
    private final NutritionEntryRepository nutritionEntryRepository;
    private final GoalRepository goalRepository;
    private final CheckInRepository checkInRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PersonalRecordService personalRecordService;
    private final PasswordEncoder passwordEncoder;

    public SeedDataRunner(UserRepository userRepository, CoachProfileRepository coachProfileRepository,
                           ClientProfileRepository clientProfileRepository, CoachClientRepository coachClientRepository,
                           ExerciseRepository exerciseRepository, ProgramRepository programRepository,
                           ProgramDayRepository programDayRepository, ProgramExerciseRepository programExerciseRepository,
                           ClientProgramRepository clientProgramRepository, WorkoutSessionRepository workoutSessionRepository,
                           WorkoutExerciseRepository workoutExerciseRepository, WorkoutSetRepository workoutSetRepository,
                           WeightEntryRepository weightEntryRepository, StepEntryRepository stepEntryRepository,
                           NutritionEntryRepository nutritionEntryRepository, GoalRepository goalRepository,
                           CheckInRepository checkInRepository, ConversationRepository conversationRepository,
                           MessageRepository messageRepository, PersonalRecordService personalRecordService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.coachClientRepository = coachClientRepository;
        this.exerciseRepository = exerciseRepository;
        this.programRepository = programRepository;
        this.programDayRepository = programDayRepository;
        this.programExerciseRepository = programExerciseRepository;
        this.clientProgramRepository = clientProgramRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.stepEntryRepository = stepEntryRepository;
        this.nutritionEntryRepository = nutritionEntryRepository;
        this.goalRepository = goalRepository;
        this.checkInRepository = checkInRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.personalRecordService = personalRecordService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already has data - skipping seed.");
            return;
        }
        log.info("Seeding FitnessHub demo data...");

        Map<String, Exercise> exercises = seedExercises();
        User coach = seedCoach();
        List<User> clients = seedClients(coach);

        // Upper/Lower A and B intentionally use different exercises (as a real A/B split would) so a
        // single exercise's progress history isn't a confusing mix of two unrelated rep/weight schemes.
        Program strengthProgram = seedProgram(coach, "12-Week Strength Program", FitnessGoal.STRENGTH, 12, exercises,
                new String[]{"Bench Press", "Barbell Row", "Overhead Press", "Lat Pulldown"},
                new String[]{"Squat", "Romanian Deadlift", "Leg Press", "Calf Raise"},
                new String[]{"Incline Dumbbell Press", "Seated Cable Row", "Lateral Raise", "Face Pull"},
                new String[]{"Deadlift", "Leg Extension", "Leg Curl", "Hip Thrust"});
        Program hypertrophyProgram = seedProgram(coach, "8-Week Hypertrophy Program", FitnessGoal.MUSCLE_GAIN, 8, exercises,
                new String[]{"Incline Dumbbell Press", "Seated Cable Row", "Lateral Raise", "Tricep Pushdown"},
                new String[]{"Deadlift", "Leg Extension", "Leg Curl", "Hip Thrust"},
                new String[]{"Cable Fly", "Pull-up", "Bicep Curl", "Face Pull"},
                new String[]{"Squat", "Romanian Deadlift", "Walking Lunge", "Calf Raise"});

        assignProgram(clients.get(0), strengthProgram, 42);
        assignProgram(clients.get(1), hypertrophyProgram, 28);
        assignProgram(clients.get(2), strengthProgram, 35);
        assignProgram(clients.get(3), strengthProgram, 28);
        assignProgram(clients.get(4), hypertrophyProgram, 5);

        seedClient1_StrengthSuccess(clients.get(0), strengthProgram, exercises);
        seedClient2_FatLossProgressing(clients.get(1), hypertrophyProgram, exercises);
        seedClient3_PlateauAttentionNeeded(clients.get(2), strengthProgram, exercises);
        seedClient4_InconsistentLogging(clients.get(3), strengthProgram);
        seedClient5_NewClient(clients.get(4));

        seedMessages(coach, clients);

        log.info("Seed complete: 1 coach, {} clients, {} exercises, 2 programs.", clients.size(), exercises.size());
    }

    // ---------------------------------------------------------------- core

    private Map<String, Exercise> seedExercises() {
        Object[][] defs = {
                {"Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, MovementPattern.PUSH_HORIZONTAL},
                {"Incline Dumbbell Press", MuscleGroup.CHEST, Equipment.DUMBBELL, MovementPattern.PUSH_HORIZONTAL},
                {"Cable Fly", MuscleGroup.CHEST, Equipment.CABLE, MovementPattern.ISOLATION},
                {"Push-up", MuscleGroup.CHEST, Equipment.BODYWEIGHT, MovementPattern.PUSH_HORIZONTAL},
                {"Barbell Row", MuscleGroup.BACK, Equipment.BARBELL, MovementPattern.PULL_HORIZONTAL},
                {"Seated Cable Row", MuscleGroup.BACK, Equipment.CABLE, MovementPattern.PULL_HORIZONTAL},
                {"Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE, MovementPattern.PULL_VERTICAL},
                {"Pull-up", MuscleGroup.BACK, Equipment.BODYWEIGHT, MovementPattern.PULL_VERTICAL},
                {"Overhead Press", MuscleGroup.SHOULDERS, Equipment.BARBELL, MovementPattern.PUSH_VERTICAL},
                {"Lateral Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, MovementPattern.ISOLATION},
                {"Face Pull", MuscleGroup.SHOULDERS, Equipment.CABLE, MovementPattern.ISOLATION},
                {"Bicep Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL, MovementPattern.ISOLATION},
                {"Tricep Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE, MovementPattern.ISOLATION},
                {"Squat", MuscleGroup.QUADS, Equipment.BARBELL, MovementPattern.SQUAT},
                {"Leg Press", MuscleGroup.QUADS, Equipment.MACHINE, MovementPattern.SQUAT},
                {"Leg Extension", MuscleGroup.QUADS, Equipment.MACHINE, MovementPattern.ISOLATION},
                {"Walking Lunge", MuscleGroup.QUADS, Equipment.DUMBBELL, MovementPattern.LUNGE},
                {"Romanian Deadlift", MuscleGroup.HAMSTRINGS, Equipment.BARBELL, MovementPattern.HINGE},
                {"Leg Curl", MuscleGroup.HAMSTRINGS, Equipment.MACHINE, MovementPattern.ISOLATION},
                {"Deadlift", MuscleGroup.BACK, Equipment.BARBELL, MovementPattern.HINGE},
                {"Hip Thrust", MuscleGroup.GLUTES, Equipment.BARBELL, MovementPattern.HINGE},
                {"Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE, MovementPattern.ISOLATION},
                {"Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT, MovementPattern.ISOLATION},
                {"Kettlebell Swing", MuscleGroup.FULL_BODY, Equipment.KETTLEBELL, MovementPattern.HINGE},
        };
        Map<String, Exercise> byName = new HashMap<>();
        for (Object[] d : defs) {
            Exercise e = new Exercise((String) d[0], (MuscleGroup) d[1], (Equipment) d[2], (MovementPattern) d[3]);
            e.setDescription("Standard " + d[0] + " performed with controlled tempo and full range of motion.");
            e = exerciseRepository.save(e);
            byName.put(e.getName(), e);
        }
        return byName;
    }

    private User seedCoach() {
        User coach = new User("coach@fitnesshub.local", passwordEncoder.encode(DEMO_PASSWORD),
                "Jordan", "Reyes", Role.COACH);
        coach.setEmailVerified(true);
        coach = userRepository.save(coach);
        CoachProfile profile = new CoachProfile(coach.getId());
        profile.setBusinessName("Reyes Performance Coaching");
        profile.setBio("Strength & physique coach, 8 years experience.");
        profile.setSpecialties("Strength training, hypertrophy, fat loss");
        profile.setYearsExperience(8);
        coachProfileRepository.save(profile);
        return coach;
    }

    private List<User> seedClients(User coach) {
        List<User> clients = new ArrayList<>();
        Object[][] defs = {
                {"client1@fitnesshub.local", "Alex", "Morgan", FitnessGoal.STRENGTH, ActivityLevel.VERY_ACTIVE, Sex.MALE, "205", "225"},
                {"client2@fitnesshub.local", "Sam", "Chen", FitnessGoal.FAT_LOSS, ActivityLevel.MODERATELY_ACTIVE, Sex.FEMALE, "172", "155"},
                {"client3@fitnesshub.local", "Riley", "Patel", FitnessGoal.STRENGTH, ActivityLevel.MODERATELY_ACTIVE, Sex.MALE, "198", "225"},
                {"client4@fitnesshub.local", "Casey", "Nguyen", FitnessGoal.GENERAL_FITNESS, ActivityLevel.LIGHTLY_ACTIVE, Sex.FEMALE, "160", "150"},
                {"client5@fitnesshub.local", "Jamie", "Ortiz", FitnessGoal.RECOMPOSITION, ActivityLevel.MODERATELY_ACTIVE, Sex.OTHER, "170", "165"},
        };
        for (Object[] d : defs) {
            User client = new User((String) d[0], passwordEncoder.encode(DEMO_PASSWORD), (String) d[1], (String) d[2], Role.CLIENT);
            client.setEmailVerified(true);
            client.setLastLoginAt(Instant.now().minusSeconds(3600));
            client = userRepository.save(client);

            ClientProfile profile = new ClientProfile(client.getId(), coach.getId());
            profile.setFitnessGoal((FitnessGoal) d[3]);
            profile.setActivityLevel((ActivityLevel) d[4]);
            profile.setSex((Sex) d[5]);
            profile.setDateOfBirth(LocalDate.now().minusYears(28));
            profile.setHeightCm(new BigDecimal(d[5] == Sex.FEMALE ? "165" : "178"));
            profile.setStartingWeight(new BigDecimal((String) d[6]));
            profile.setTargetWeight(new BigDecimal((String) d[7]));
            profile.setDailyCalorieTarget(new BigDecimal("2400"));
            profile.setDailyProteinTarget(new BigDecimal("160"));
            clientProfileRepository.save(profile);

            coachClientRepository.save(new CoachClient(coach.getId(), client.getId()));
            clients.add(client);
        }
        return clients;
    }

    private Program seedProgram(User coach, String name, FitnessGoal goal, int weeks, Map<String, Exercise> exercises,
                                 String[] upperAExercises, String[] lowerAExercises,
                                 String[] upperBExercises, String[] lowerBExercises) {
        Program program = new Program(coach.getId(), name, weeks, goal);
        program.setDescription("A " + weeks + "-week coach-built program focused on " + goal.name().toLowerCase().replace('_', ' ') + ".");
        program = programRepository.save(program);

        addDay(program, 1, "Upper A", upperAExercises, exercises);
        addDay(program, 2, "Lower A", lowerAExercises, exercises);
        programDayRepository.save(new ProgramDay(program.getId(), 3, "Rest"));
        addDay(program, 4, "Upper B", upperBExercises, exercises);
        addDay(program, 5, "Lower B", lowerBExercises, exercises);
        return program;
    }

    private void addDay(Program program, int dayNumber, String name, String[] exerciseNames, Map<String, Exercise> exercises) {
        ProgramDay day = programDayRepository.save(new ProgramDay(program.getId(), dayNumber, name));
        int order = 0;
        for (String exerciseName : exerciseNames) {
            Exercise exercise = exercises.get(exerciseName);
            ProgramExercise pe = new ProgramExercise(day.getId(), exercise.getId(), order++, 4, 8);
            pe.setTargetWeight(new BigDecimal("135"));
            pe.setTargetRpe(new BigDecimal("8"));
            pe.setRestSeconds(150);
            programExerciseRepository.save(pe);
        }
    }

    private void assignProgram(User client, Program program, int daysAgoStarted) {
        clientProgramRepository.save(new ClientProgram(client.getId(), program.getId(),
                LocalDate.now().minusDays(daysAgoStarted)));
    }

    // ----------------------------------------------------------- narratives

    /** Client 1: bench press progressing exactly as in spec section 146, high adherence, several PRs. */
    private void seedClient1_StrengthSuccess(User client, Program program, Map<String, Exercise> exercises) {
        UUID id = client.getId();
        weightSeries(id, 205, -0.1, 42, 3);
        stepSeries(id, 9500, 300, 42);
        nutritionSeries(id, 2350, 175, 42, 1.0);
        goalRepository.save(newGoal(id, GoalType.STRENGTH, "Bench Press 225 lb", "225", "205", "lb"));
        goalRepository.save(newGoal(id, GoalType.WORKOUT_ADHERENCE, "90% Adherence", "90", "88", "%"));
        checkInSeries(id, 42, 4, 4, 2, 4, 4, 4);

        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(program.getId());
        BigDecimal[] benchWeekly = {new BigDecimal("200"), new BigDecimal("195"), new BigDecimal("190"), new BigDecimal("185")};
        int[] benchRepsWeekly = {6, 7, 8, 8};
        BigDecimal[] benchRpeWeekly = {new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("8.5"), new BigDecimal("8")};

        for (int week = 6; week >= 1; week--) {
            for (ProgramDay day : days) {
                if (day.getName().equals("Rest")) continue;
                if (week == 3 && day.getName().equals("Lower A")) continue; // one missed session for realism
                int daysAgo = (week - 1) * 7 + (5 - day.getDayNumber());

                WorkoutSession session = workoutSessionRepository.save(backdatedSession(id, day.getId(), daysAgo));
                List<ProgramExercise> dayExercises = programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId());
                for (int i = 0; i < dayExercises.size(); i++) {
                    ProgramExercise pe = dayExercises.get(i);
                    Exercise exercise = findById(exercises, pe.getExerciseId());
                    if (day.getName().equals("Upper A") && exercise.getName().equals("Bench Press") && week <= 4) {
                        int idx = week - 1;
                        addExerciseWithProgressiveSets(session, pe.getExerciseId(), i, daysAgo,
                                benchWeekly[idx], benchRepsWeekly[idx], benchRpeWeekly[idx]);
                    } else {
                        BigDecimal baseWeight = new BigDecimal(100 + i * 15 + (6 - week) * 2);
                        addExerciseWithProgressiveSets(session, pe.getExerciseId(), i, daysAgo,
                                baseWeight, 8, new BigDecimal("7.5"));
                    }
                }
                finishSession(session, id, 52);
            }
        }
    }

    /** Client 2: fat loss, weight trending down, steps improving. */
    private void seedClient2_FatLossProgressing(User client, Program program, Map<String, Exercise> exercises) {
        UUID id = client.getId();
        weightSeries(id, 172, -0.35, 28, 3);
        stepSeriesImproving(id, 6500, 9800, 28);
        nutritionSeries(id, 1850, 140, 28, 0.9);
        goalRepository.save(newGoal(id, GoalType.WEIGHT, "Reach 155 lb", "155", "163", "lb"));
        goalRepository.save(newGoal(id, GoalType.STEPS, "10,000 steps/day", "10000", "8700", "steps"));
        checkInSeries(id, 28, 4, 3, 3, 3, 4, 5);

        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(program.getId());
        for (int week = 4; week >= 1; week--) {
            for (ProgramDay day : days) {
                if (day.getName().equals("Rest") || day.getName().equals("Lower B")) continue;
                int daysAgo = (week - 1) * 7 + (5 - day.getDayNumber());
                WorkoutSession session = workoutSessionRepository.save(backdatedSession(id, day.getId(), daysAgo));
                List<ProgramExercise> dayExercises = programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId());
                for (int i = 0; i < dayExercises.size(); i++) {
                    BigDecimal baseWeight = new BigDecimal(60 + i * 10 + (4 - week));
                    addExerciseWithProgressiveSets(session, dayExercises.get(i).getExerciseId(), i, daysAgo,
                            baseWeight, 10, new BigDecimal("7"));
                }
                finishSession(session, id, 40);
            }
        }
    }

    /** Client 3: strength plateau, high RPE, low recovery -> should trip attention flags. */
    private void seedClient3_PlateauAttentionNeeded(User client, Program program, Map<String, Exercise> exercises) {
        UUID id = client.getId();
        weightSeries(id, 198, 0.0, 35, 3);
        stepSeries(id, 6000, -50, 35);
        nutritionSeries(id, 2600, 150, 35, 0.3); // sparse logging -> CALORIE_TRACKING_INCONSISTENT
        goalRepository.save(newGoal(id, GoalType.STRENGTH, "Squat 315 lb", "315", "295", "lb"));
        checkIn(id, 30, 2, 2, 2, 4, 3, 3); // only an old check-in -> NO_RECENT_CHECKIN

        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(program.getId());
        for (int week = 5; week >= 1; week--) {
            for (ProgramDay day : days) {
                if (day.getName().equals("Rest")) continue;
                int daysAgo = (week - 1) * 7 + (5 - day.getDayNumber());
                WorkoutSession session = workoutSessionRepository.save(backdatedSession(id, day.getId(), daysAgo));
                List<ProgramExercise> dayExercises = programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId());
                for (int i = 0; i < dayExercises.size(); i++) {
                    BigDecimal weight = new BigDecimal(225 - i * 30);
                    int reps = week <= 2 ? 4 : 5; // recent sessions: fewer reps at the same weight = regressing
                    addExerciseWithFixedSets(session, dayExercises.get(i).getExerciseId(), i, daysAgo,
                            weight, reps, new BigDecimal("9.5"), 4);
                }
                finishSession(session, id, 55);
            }
        }
    }

    /** Client 4: inconsistent logging, low adherence. */
    private void seedClient4_InconsistentLogging(User client, Program program) {
        UUID id = client.getId();
        weightSeries(id, 160, -0.05, 20, 5);
        stepSeries(id, 5200, 0, 20);
        nutritionSeries(id, 1900, 100, 20, 0.3);
        goalRepository.save(newGoal(id, GoalType.WORKOUT_ADHERENCE, "Train 3x/week", "3", "1", "sessions/week"));

        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(program.getId());
        ProgramDay upperA = days.stream().filter(d -> d.getName().equals("Upper A")).findFirst().orElseThrow();
        List<ProgramExercise> upperAExercises = programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(upperA.getId());

        int[] daysAgoValues = {18, 12};
        BigDecimal[] weights = {new BigDecimal("60"), new BigDecimal("62.5")};
        int[] repsValues = {10, 9};
        for (int s = 0; s < daysAgoValues.length; s++) {
            WorkoutSession session = workoutSessionRepository.save(backdatedSession(id, upperA.getId(), daysAgoValues[s]));
            for (int i = 0; i < upperAExercises.size(); i++) {
                addExerciseWithFixedSets(session, upperAExercises.get(i).getExerciseId(), i, daysAgoValues[s],
                        weights[s], repsValues[s], new BigDecimal("7.5"), 3);
            }
            finishSession(session, id, 30);
        }
    }

    /** Client 5: brand-new, almost no history. */
    private void seedClient5_NewClient(User client) {
        UUID id = client.getId();
        weightSeries(id, 170, 0.0, 3, 0.5);
        goalRepository.save(newGoal(id, GoalType.CUSTOM, "Establish a training routine", "1", "0", "habit"));
    }

    // ------------------------------------------------------------ helpers

    private WorkoutSession backdatedSession(UUID clientId, UUID programDayId, int daysAgo) {
        WorkoutSession session = new WorkoutSession(clientId, programDayId);
        session.setStartedAt(Instant.now().minus(Duration.ofDays(daysAgo)).minusSeconds(3600));
        return session;
    }

    /** Adds one exercise with progressively heavier sets (last set is the "top set" used for PR checks). */
    private void addExerciseWithProgressiveSets(WorkoutSession session, UUID exerciseId, int orderIndex, int daysAgo,
                                                 BigDecimal topWeight, int topReps, BigDecimal topRpe) {
        WorkoutExercise we = workoutExerciseRepository.save(new WorkoutExercise(session.getId(), exerciseId, orderIndex));
        Instant baseTime = session.getStartedAt().plusSeconds(600L * orderIndex);

        WorkoutSet warmup = newSet(we.getId(), 1, 10, topWeight.multiply(new BigDecimal("0.5")), baseTime);
        warmup.setWarmup(true);
        workoutSetRepository.save(warmup);

        BigDecimal[] fractions = {new BigDecimal("0.75"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.0")};
        int[] repsByFraction = {topReps + 2, topReps + 1, topReps, topReps};
        for (int i = 0; i < 4; i++) {
            BigDecimal weight = topWeight.multiply(fractions[i]).setScale(1, RoundingMode.HALF_UP);
            WorkoutSet set = newSet(we.getId(), i + 2, repsByFraction[i], weight, baseTime.plusSeconds(180L * (i + 1)));
            set.setRpe(i < 2 ? topRpe.subtract(BigDecimal.ONE) : topRpe);
            workoutSetRepository.save(set);
            personalRecordService.checkSetForRecords(session.getClientId(), exerciseId, set);
        }
    }

    /** Adds one exercise where every working set is identical - used for plateau/regression narratives. */
    private void addExerciseWithFixedSets(WorkoutSession session, UUID exerciseId, int orderIndex, int daysAgo,
                                           BigDecimal weight, int reps, BigDecimal rpe, int setCount) {
        WorkoutExercise we = workoutExerciseRepository.save(new WorkoutExercise(session.getId(), exerciseId, orderIndex));
        Instant baseTime = session.getStartedAt().plusSeconds(600L * orderIndex);
        for (int i = 0; i < setCount; i++) {
            WorkoutSet set = newSet(we.getId(), i + 1, reps, weight, baseTime.plusSeconds(180L * i));
            set.setRpe(rpe);
            workoutSetRepository.save(set);
            personalRecordService.checkSetForRecords(session.getClientId(), exerciseId, set);
        }
    }

    private WorkoutSet newSet(UUID workoutExerciseId, int setNumber, int reps, BigDecimal weight, Instant completedAt) {
        WorkoutSet set = new WorkoutSet(workoutExerciseId, setNumber, reps, weight);
        set.setCompletedAt(completedAt);
        return set;
    }

    private void finishSession(WorkoutSession session, UUID clientId, int durationMinutes) {
        session.setCompletedAt(session.getStartedAt().plusSeconds(60L * durationMinutes));
        session.setDurationSeconds(durationMinutes * 60);
        session.setStatus(WorkoutSessionStatus.COMPLETED);
        workoutSessionRepository.save(session);

        for (WorkoutExercise we : workoutExerciseRepository.findByWorkoutSessionIdOrderByOrderIndexAsc(session.getId())) {
            List<WorkoutSet> sets = workoutSetRepository.findByWorkoutExerciseIdOrderBySetNumberAsc(we.getId());
            if (sets.isEmpty()) continue;
            UUID representativeSetId = sets.get(sets.size() - 1).getId();
            personalRecordService.checkSessionVolumeForRecord(clientId, we.getExerciseId(), sets, representativeSetId, session.getCompletedAt());
        }
    }

    private Exercise findById(Map<String, Exercise> exercises, UUID id) {
        return exercises.values().stream().filter(e -> e.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown seeded exercise id " + id));
    }

    private Goal newGoal(UUID clientId, GoalType type, String name, String target, String current, String unit) {
        Goal goal = new Goal(clientId, type, name, new BigDecimal(target), unit);
        goal.setCurrentValue(new BigDecimal(current));
        return goal;
    }

    private void weightSeries(UUID clientId, double startWeight, double dailyTrend, int totalDays, double noise) {
        double weight = startWeight;
        for (int d = totalDays; d >= 0; d -= 2) {
            double jitter = (Math.random() - 0.5) * noise;
            WeightEntry entry = new WeightEntry(clientId,
                    BigDecimal.valueOf(weight + jitter).setScale(1, RoundingMode.HALF_UP),
                    Instant.now().minus(Duration.ofDays(d)));
            weightEntryRepository.save(entry);
            weight += dailyTrend * 2;
        }
    }

    private void stepSeries(UUID clientId, int baseSteps, int weeklyTrend, int totalDays) {
        for (int d = totalDays; d >= 0; d--) {
            int steps = Math.max(0, baseSteps + (int) (weeklyTrend * (totalDays - d) / 7.0) + (int) (Math.random() * 1500 - 750));
            stepEntryRepository.save(new StepEntry(clientId, steps, LocalDate.now().minusDays(d), StepSource.MANUAL));
        }
    }

    private void stepSeriesImproving(UUID clientId, int startSteps, int endSteps, int totalDays) {
        for (int d = totalDays; d >= 0; d--) {
            double progress = 1.0 - (d / (double) totalDays);
            int steps = (int) (startSteps + (endSteps - startSteps) * progress + (Math.random() * 1000 - 500));
            stepEntryRepository.save(new StepEntry(clientId, Math.max(0, steps), LocalDate.now().minusDays(d), StepSource.MANUAL));
        }
    }

    private void nutritionSeries(UUID clientId, int baseCalories, int baseProtein, int daysLogged, double consistency) {
        for (int d = daysLogged; d >= 0; d--) {
            if (Math.random() > consistency) continue; // simulate inconsistent logging
            int calories = baseCalories + (int) (Math.random() * 300 - 150);
            int protein = baseProtein + (int) (Math.random() * 20 - 10);
            NutritionEntry entry = new NutritionEntry(clientId, LocalDate.now().minusDays(d),
                    BigDecimal.valueOf(calories), BigDecimal.valueOf(protein));
            entry.setCarbohydratesGrams(BigDecimal.valueOf(calories * 0.4 / 4));
            entry.setFatGrams(BigDecimal.valueOf(calories * 0.3 / 9));
            entry.setWaterMl(2000);
            nutritionEntryRepository.save(entry);
        }
    }

    private void checkInSeries(UUID clientId, int totalDays, int... scores) {
        int weeks = totalDays / 7;
        for (int w = weeks; w >= 1; w--) {
            checkIn(clientId, w * 7, scores[0], scores[1], scores[2], scores[3], scores[4], scores[5]);
        }
    }

    private void checkIn(UUID clientId, int daysAgo, int energy, int sleep, int stress, int hunger,
                          int workoutAdherence, int nutritionAdherence) {
        LocalDate weekStart = LocalDate.now().minusDays(daysAgo)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        if (checkInRepository.findByClientIdAndWeekStartDate(clientId, weekStart).isPresent()) return;
        CheckIn checkIn = new CheckIn(clientId, weekStart);
        checkIn.setEnergyScore(clamp(energy));
        checkIn.setSleepScore(clamp(sleep));
        checkIn.setStressScore(clamp(stress));
        checkIn.setHungerScore(clamp(hunger));
        checkIn.setWorkoutAdherence(clamp(workoutAdherence));
        checkIn.setNutritionAdherence(clamp(nutritionAdherence));
        checkInRepository.save(checkIn);
    }

    private int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }

    private void seedMessages(User coach, List<User> clients) {
        for (User client : clients) {
            Conversation conv = conversationRepository.save(new Conversation(coach.getId(), client.getId()));
            messageRepository.save(new Message(conv.getId(), coach.getId(),
                    "Hey " + client.getFirstName() + "! Welcome aboard - let me know if you have any questions about the program."));
            Message reply = new Message(conv.getId(), client.getId(), "Thanks! Excited to get started.");
            reply.setReadAt(Instant.now());
            messageRepository.save(reply);
        }
    }
}
