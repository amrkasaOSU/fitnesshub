export type Role = "ADMIN" | "COACH" | "CLIENT";

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  profileImageUrl?: string | null;
  timezone: string;
  emailVerified: boolean;
  lastLoginAt?: string | null;
  createdAt: string;
}

export type FitnessGoal =
  | "FAT_LOSS"
  | "MUSCLE_GAIN"
  | "STRENGTH"
  | "GENERAL_FITNESS"
  | "ATHLETIC_PERFORMANCE"
  | "RECOMPOSITION";

export interface PageResponse<T> {
  data: T[];
  page: number;
  pageSize: number;
  totalPages: number;
  totalItems: number;
}

export interface WorkoutSetDto {
  id: string;
  setNumber: number;
  reps: number;
  weight: number;
  rpe: number | null;
  rir: number | null;
  isWarmup: boolean;
  isFailure: boolean;
  completedAt: string;
}

export interface PreviousPerformanceDto {
  date: string;
  weight: number;
  reps: number;
  rpe: number | null;
}

export interface WorkoutExerciseDto {
  workoutExerciseId: string | null;
  exerciseId: string;
  exerciseName: string;
  orderIndex: number;
  targetSets: number | null;
  targetReps: number | null;
  targetWeight: number | null;
  targetRpe: number | null;
  restSeconds: number | null;
  progressionStrategy: string | null;
  previousPerformance: PreviousPerformanceDto | null;
  completedSets: WorkoutSetDto[];
}

export interface WorkoutFeedbackDto {
  id: string;
  content: string;
  createdAt: string;
}

export interface WorkoutSessionDto {
  id: string;
  dayName: string;
  status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED";
  startedAt: string;
  completedAt: string | null;
  durationSeconds: number | null;
  notes: string | null;
  overallRpe: number | null;
  energyLevel: number | null;
  totalVolume: number;
  prCount: number;
  exercises: WorkoutExerciseDto[];
  feedback: WorkoutFeedbackDto[];
  skipReason?: string | null;
}

export interface WorkoutSummaryDto {
  id: string;
  dayName: string;
  startedAt: string;
  durationSeconds: number | null;
  totalVolume: number;
  averageRpe: number | null;
  status: WorkoutSessionDto["status"];
  prCount: number;
  skipReason?: string | null;
}

/** One square in the Monday-Sunday week strip on the client's Today page. */
export interface WeekDayDto {
  date: string;
  dayOfWeek: string;
  today: boolean;
  past: boolean;
  programDayId?: string | null;
  programDayName?: string | null;
  restDay: boolean;
  sessionId?: string | null;
  status?: WorkoutSessionDto["status"] | null;
  skipReason?: string | null;
  shifted: boolean;
}

export interface WeightDashboardDto {
  current: number | null;
  starting: number | null;
  lowest: number | null;
  highest: number | null;
  weeklyAverage: number | null;
  weeklyAverageMessage: string | null;
  weeklyChange: number | null;
  monthlyChange: number | null;
  totalChange: number | null;
  goalWeight: number | null;
}

export interface StepDashboardDto {
  todaySteps: number;
  weeklyAverage: number;
  sevenDayAverage: number;
  weeklyTotal: number;
  goal: number;
  goalCompletionPercentage: number;
}

export interface CalorieEstimate {
  available: boolean;
  estimatedActiveCalories: number | null;
  estimatedTotalCalories: number | null;
  note: string;
}

export interface NutritionDashboardDto {
  caloriesConsumedToday: number;
  calorieTarget: number | null;
  caloriesRemaining: number | null;
  proteinConsumedToday: number;
  proteinTarget: number | null;
  proteinRemaining: number | null;
  sevenDayAverageCalories: number;
  sevenDayAverageProtein: number;
  estimatedCaloriesBurned: CalorieEstimate;
  estimatedCalorieBalance: number | null;
}

export interface AdherenceResult {
  completedWorkouts: number;
  plannedWorkouts: number;
  adherencePercentage: number | null;
}

export interface GoalDto {
  id: string;
  type: string;
  name: string;
  targetValue: number;
  currentValue: number;
  unit: string;
  targetDate: string | null;
  status: "ACTIVE" | "ACHIEVED" | "ABANDONED";
  percentComplete: number | null;
}

export interface PersonalRecordDto {
  id: string;
  clientId: string;
  exerciseId: string;
  exerciseName: string;
  recordType: "MAX_WEIGHT" | "MAX_REPS" | "ESTIMATED_1RM" | "MAX_VOLUME";
  value: string;
  achievedAt: string;
}

export interface ClientDashboardDto {
  todaysWorkout: WorkoutSessionDto;
  nutrition: NutritionDashboardDto;
  steps: StepDashboardDto;
  weight: WeightDashboardDto;
  weeklyAdherence: AdherenceResult;
  recentPrs: PersonalRecordDto[];
  activeGoals: GoalDto[];
  unreadMessages: number;
  unreadNotifications: number;
}

export interface ClientSummaryDto {
  clientId: string;
  firstName: string;
  lastName: string;
  email: string;
  goal: FitnessGoal | null;
  currentWeight: number | null;
  weeklyWeightChange: number | null;
  adherencePercentage: number | null;
  lastWorkoutAt: string | null;
  todaySteps: number;
  caloriesToday: number;
  attentionFlags: string[];
}

export interface CoachDashboardDto {
  totalClients: number;
  activeClients: number;
  clientsTrainingToday: number;
  averageWeeklyAdherence: number | null;
  averageWeeklyWeightChange: number | null;
  clientsNeedingAttention: ClientSummaryDto[];
  recentPrs: PersonalRecordDto[];
  unreadMessages: number;
  checkInsPendingReview: number;
}

export interface ExerciseDto {
  id: string;
  name: string;
  description: string | null;
  muscleGroup: string;
  equipment: string;
  movementPattern: string;
  instructions: string | null;
  videoUrl: string | null;
  imageUrl: string | null;
  isSystemExercise: boolean;
}

export interface ExerciseProgressSessionPoint {
  date: string;
  topWeight: number;
  topWeightReps: number;
  estimated1Rm: number;
  volume: number;
  averageRpe: number | null;
}

export interface ExerciseProgressSummary {
  bestWeight: number;
  bestReps: number;
  bestEstimated1Rm: number;
  latestVolume: number;
  previousVolume: number;
  volumeTrend: string;
  performanceTrend: string;
  recentAverageRpe: number | null;
  recentFailureRate: number | null;
  recentSessions: ExerciseProgressSessionPoint[];
}

export interface MessageDto {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  createdAt: string;
  readAt: string | null;
}

export interface ConversationDto {
  id: string;
  coachId: string;
  clientId: string;
  otherPartyName: string;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface NotificationDto {
  id: string;
  type: string;
  title: string;
  body: string | null;
  relatedType: string | null;
  relatedId: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface AiAnswer {
  answer: string;
  confidence: string;
  relevantMetrics: string[];
  recommendation: string | null;
  reasoningSummary: string | null;
  warnings: string[];
}

export interface CheckInDto {
  id: string;
  clientId: string;
  weekStartDate: string;
  weight: number | null;
  energyScore: number;
  sleepScore: number;
  stressScore: number;
  hungerScore: number;
  workoutAdherence: number;
  nutritionAdherence: number;
  notes: string | null;
  submittedAt: string;
  coachReviewedAt: string | null;
  coachResponse: string | null;
  needsReview: boolean;
}

export interface SetCompletionResult {
  set: WorkoutSetDto;
  newPersonalRecords: { recordType: string; value: string }[];
}

export interface ClientDetailDto {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  coachId: string;
  dateOfBirth: string | null;
  heightCm: number | null;
  sex: string | null;
  fitnessGoal: FitnessGoal;
  activityLevel: string;
  startingWeight: number | null;
  targetWeight: number | null;
  dailyCalorieTarget: number | null;
  dailyProteinTarget: number | null;
  dailyStepTarget: number | null;
  unitSystem: string;
}

export interface TrainingSummaryDto {
  workoutsCompleted: number;
  workoutsPlanned: number;
  adherencePercentage: number | null;
  totalSets: number;
  totalReps: number;
  totalVolume: number;
  averageWorkoutDurationMinutes: number | null;
  averageRpe: number | null;
  averageRir: number | null;
  prCount: number;
}

export interface ProgramSummaryDto {
  id: string;
  name: string;
  durationWeeks: number;
  goal: FitnessGoal;
  version: number;
  dayCount: number;
  assignedClientCount: number;
}
