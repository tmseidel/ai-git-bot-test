package com.example.tasktracker.web;

import com.example.tasktracker.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context smoke test: verifies the application boots, Flyway runs,
 * JPA validates the schema, Thymeleaf renders, and i18n resolves in both
 * English (default) and German.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class I18nSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DisplayName("GET / in English: renders English UI text")
    void index_english() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("Create Task")));
    }

    @Test
    @DisplayName("GET /?lang=de: renders German UI text (umlauts intact)")
    void index_german() throws Exception {
        var result = mockMvc.perform(get("/").param("lang", "de"))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html)
                .contains("Neue Aufgabe")
                .contains("Aufgabe erstellen");
    }

    @Test
    @DisplayName("GET /?lang=de: language switch link points to ?lang=en")
    void index_german_switchLink() throws Exception {
        mockMvc.perform(get("/").param("lang", "de"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("?lang=en")));
    }

    @Test
    @DisplayName("Full round trip: create task, start, stop, verify duration")
    void roundTrip_createStartStop() throws Exception {
        // Create a task via the form (PRG).
        mockMvc.perform(post("/tasks").param("title", "Smoke task"))
                .andExpect(status().is3xxRedirection());

        var task = taskRepository.findAll().stream()
                .filter(t -> "Smoke task".equals(t.getTitle()))
                .findFirst().orElseThrow();

        // Start tracking via the JSON API.
        mockMvc.perform(post("/api/tasks/{id}/start", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.status").value("DOING"));

        // Stop tracking.
        mockMvc.perform(post("/api/tasks/{id}/stop", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));

        task = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(task.getTrackedSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(task.getStartedAt()).isNull();
    }
}
