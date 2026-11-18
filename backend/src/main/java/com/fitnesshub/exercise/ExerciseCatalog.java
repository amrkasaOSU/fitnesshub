package com.fitnesshub.exercise;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitnesshub.exercise.dto.ExerciseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The exercise library, cached in Redis.
 *
 * <p>The library is the textbook cache candidate: it is read on nearly every
 * screen (program builder, today's workout, history, the AI assistant's
 * exercise matching) and written almost never - only when a coach adds a
 * custom exercise. So the whole list is stored as one JSON value, and reads
 * are served from it without touching PostgreSQL or taking a connection from
 * the pool.
 *
 * <p>Design choices worth knowing:
 * <ul>
 *   <li><b>Redis, not an in-process map</b>, so every backend instance sees a
 *       new exercise the moment the cache is evicted, not whenever its own
 *       copy happens to expire.</li>
 *   <li><b>Evict on write</b>, with a TTL only as a safety net for anything
 *       that changes the table outside this class (a migration, a manual
 *       fix).</li>
 *   <li><b>Redis being down is not an outage.</b> Any cache failure falls
 *       through to the database, logged once at WARN.</li>
 * </ul>
 */
@Service
public class ExerciseCatalog {

    private static final Logger log = LoggerFactory.getLogger(ExerciseCatalog.class);
    /** Versioned so a change to {@link ExerciseDto}'s shape can't deserialize a stale payload. */
    static final String CACHE_KEY = "fitnesshub:exercise-catalog:v1";
    private static final Duration TTL = Duration.ofHours(1);
    private static final TypeReference<List<ExerciseDto>> LIST_TYPE = new TypeReference<>() { };

    private final ExerciseRepository exerciseRepository;
    private final ExerciseMapper exerciseMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ExerciseCatalog(ExerciseRepository exerciseRepository, ExerciseMapper exerciseMapper,
                           StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseMapper = exerciseMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Every exercise, ordered by name exactly as the database orders them. */
    public List<ExerciseDto> all() {
        List<ExerciseDto> cached = readCache();
        if (cached != null) {
            return cached;
        }
        List<ExerciseDto> fresh = loadFromDatabase();
        writeCache(fresh);
        return fresh;
    }

    /** Names for a set of exercise ids, without a query. Unknown ids are absent. */
    public Map<UUID, String> namesById() {
        return all().stream().collect(Collectors.toMap(ExerciseDto::id, ExerciseDto::name));
    }

    public Map<UUID, ExerciseDto> byId() {
        return all().stream().collect(Collectors.toMap(ExerciseDto::id, Function.identity()));
    }

    /** Call after anything that adds, renames or removes an exercise. */
    public void evict() {
        try {
            redis.delete(CACHE_KEY);
        } catch (RuntimeException e) {
            log.warn("Could not evict the exercise catalog cache; it will expire within {}: {}", TTL, e.toString());
        }
    }

    private List<ExerciseDto> loadFromDatabase() {
        return exerciseRepository.findAll(Sort.by("name")).stream().map(exerciseMapper::toDto).toList();
    }

    private List<ExerciseDto> readCache() {
        try {
            String json = redis.opsForValue().get(CACHE_KEY);
            return json == null ? null : objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            log.warn("Exercise catalog cache read failed; falling back to the database: {}", e.toString());
            return null;
        }
    }

    private void writeCache(List<ExerciseDto> exercises) {
        try {
            redis.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(exercises), TTL);
        } catch (Exception e) {
            log.warn("Exercise catalog cache write failed; continuing without it: {}", e.toString());
        }
    }
}
