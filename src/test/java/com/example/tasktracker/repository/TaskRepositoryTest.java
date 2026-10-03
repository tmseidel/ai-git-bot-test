package com.example.tasktracker.repository;

import com.example.tasktracker.domain.Task;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository test against a real H2 database.
 *
 * <p>Spring Boot 4.1 no longer ships a JPA test slice ({@code @DataJpaTest})
 * or {@code TestEntityManager}; this test therefore runs the full application
 * context (H2 in-memory, test profile) and uses the JPA {@link EntityManager}
 * directly for flushing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    void saveAndFindById_roundTrips() {
        var task = Task.builder()
                .title("Round trip")
                .status(Task.Status.TODO)
                .trackedSeconds(0)
                .build();

        var saved = taskRepository.saveAndFlush(task);

        entityManager.clear();
        var found = taskRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Round trip");
        assertThat(found.getStatus()).isEqualTo(Task.Status.TODO);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findAllByStartedAtIsNotNull_returnsOnlyRunning() {
        var running = Task.builder()
                .title("Running")
                .status(Task.Status.DOING)
                .trackedSeconds(0)
                .startedAt(Instant.parse("2026-01-01T11:00:00Z"))
                .build();
        var idle = Task.builder()
                .title("Idle")
                .status(Task.Status.TODO)
                .trackedSeconds(60)
                .build();
        taskRepository.saveAll(List.of(running, idle));

        List<Task> result = taskRepository.findAllByStartedAtIsNotNull();
        assertThat(result).extracting(Task::getTitle).containsExactly("Running");
    }

    @Test
    void existsByStartedAtIsNotNull_reflectsState() {
        assertThat(taskRepository.existsByStartedAtIsNotNull()).isFalse();

        var running = Task.builder()
                .title("Running")
                .status(Task.Status.DOING)
                .trackedSeconds(0)
                .startedAt(Instant.parse("2026-01-01T11:00:00Z"))
                .build();
        taskRepository.save(running);

        assertThat(taskRepository.existsByStartedAtIsNotNull()).isTrue();
    }

    @Test
    void duplicateTitle_isRejectedByUniqueIndex() {
        taskRepository.saveAndFlush(Task.builder()
                .title("Duplicate")
                .status(Task.Status.TODO)
                .trackedSeconds(0)
                .build());

        entityManager.clear();
        var second = Task.builder()
                .title("Duplicate")
                .status(Task.Status.TODO)
                .trackedSeconds(0)
                .build();

        // Hibernate 7 executes the INSERT on persist() (immediate id generation),
        // so the unique-index violation is raised here, not at flush().
        assertThatThrownBy(() -> entityManager.persist(second))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Unique index or primary key violation");
    }
}
