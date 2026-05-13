package com.codesync.project.controller;

import com.codesync.project.dto.CreateProjectRequest;
import com.codesync.project.dto.ProjectResponse;
import com.codesync.project.exception.ResourceNotFoundException;
import com.codesync.project.model.Language;
import com.codesync.project.model.Visibility;
import com.codesync.project.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProjectController}.
 *
 * Tests the controller methods directly (no HTTP stack / MockMvc).
 * HTTP concerns (serialization, auth) are better covered in manual
 * Postman / Swagger testing since the service-level tests already
 * validate all business logic thoroughly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectController Unit Tests")
class ProjectControllerTest {

    @Mock  ProjectService projectService;
    @InjectMocks ProjectController projectController;

    private static final Long USER_ID    = 1L;
    private static final Long PROJECT_ID = 10L;

    private ProjectResponse sampleResponse() {
        return ProjectResponse.builder()
                .id(PROJECT_ID).name("Test Project")
                .language(Language.JAVA).visibility(Visibility.PRIVATE)
                .ownerId(USER_ID).createdAt(LocalDateTime.now()).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createProject()")
    class Create {

        @Test
        @DisplayName("returns HTTP 201 with body on success")
        void returnsCreated() {
            CreateProjectRequest req = new CreateProjectRequest();
            req.setName("Test Project");
            req.setLanguage(Language.JAVA);

            when(projectService.createProject(req, USER_ID)).thenReturn(sampleResponse());

            ResponseEntity<ProjectResponse> resp = projectController.createProject(req, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getName()).isEqualTo("Test Project");
            verify(projectService).createProject(req, USER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProject()")
    class Get {

        @Test
        @DisplayName("returns HTTP 200 with project")
        void returnsOk() {
            when(projectService.getProjectById(PROJECT_ID, USER_ID)).thenReturn(sampleResponse());

            ResponseEntity<ProjectResponse> resp = projectController.getProject(PROJECT_ID, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException from service")
        void propagatesNotFoundException() {
            when(projectService.getProjectById(99L, USER_ID))
                    .thenThrow(new ResourceNotFoundException("Project", "id", 99L));

            assertThatThrownBy(() -> projectController.getProject(99L, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMyProjects()")
    class MyProjects {

        @Test
        @DisplayName("returns paginated project list")
        void returnsPaginatedList() {
            var page = new PageImpl<>(List.of(sampleResponse()));
            when(projectService.getMyProjects(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            var resp = projectController.getMyProjects(USER_ID, 0, 10, "createdAt", "desc");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getTotalElements()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteProject()")
    class Delete {

        @Test
        @DisplayName("returns HTTP 204 on success")
        void returnsNoContent() {
            doNothing().when(projectService).deleteProject(PROJECT_ID, USER_ID);

            ResponseEntity<Void> resp = projectController.deleteProject(PROJECT_ID, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(projectService).deleteProject(PROJECT_ID, USER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("archiveProject()")
    class Archive {

        @Test
        @DisplayName("returns HTTP 204 on success")
        void returnsNoContent() {
            doNothing().when(projectService).archiveProject(PROJECT_ID, USER_ID);

            ResponseEntity<Void> resp = projectController.archiveProject(PROJECT_ID, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
