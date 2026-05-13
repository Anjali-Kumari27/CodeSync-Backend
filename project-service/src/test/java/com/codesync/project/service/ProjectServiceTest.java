package com.codesync.project.service;

import com.codesync.project.dto.AddMemberRequest;
import com.codesync.project.dto.CreateProjectRequest;
import com.codesync.project.dto.ProjectResponse;
import com.codesync.project.dto.UpdateProjectRequest;
import com.codesync.project.exception.AccessDeniedException;
import com.codesync.project.exception.DuplicateResourceException;
import com.codesync.project.exception.ResourceNotFoundException;
import com.codesync.project.model.*;
import com.codesync.project.repository.ProjectMemberRepository;
import com.codesync.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProjectService}.
 *
 * Uses Mockito to stub repositories – no Spring context needed.
 * Each nested class covers a distinct feature/scenario group.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService Unit Tests")
class ProjectServiceTest {

    @Mock private ProjectRepository      projectRepository;
    @Mock private ProjectMemberRepository memberRepository;

    @InjectMocks private ProjectService projectService;

    // ── Shared test fixtures ───────────────────────────────────────────────

    private static final Long OWNER_ID  = 1L;
    private static final Long OTHER_ID  = 2L;
    private static final Long PROJECT_ID = 10L;

    private Project sampleProject() {
        return Project.builder()
                .id(PROJECT_ID)
                .name("My Project")
                .description("Test description")
                .language(Language.JAVA)
                .visibility(Visibility.PRIVATE)
                .ownerId(OWNER_ID)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── CREATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createProject()")
    class CreateProject {

        @Test
        @DisplayName("should create and return a project response")
        void shouldCreateProject() {
            CreateProjectRequest req = new CreateProjectRequest();
            req.setName("CodeSync IDE");
            req.setLanguage(Language.PYTHON);
            req.setVisibility(Visibility.PUBLIC);

            Project saved = Project.builder()
                    .id(1L).name("CodeSync IDE")
                    .language(Language.PYTHON).visibility(Visibility.PUBLIC)
                    .ownerId(OWNER_ID).build();

            when(projectRepository.save(any(Project.class))).thenReturn(saved);

            ProjectResponse response = projectService.createProject(req, OWNER_ID);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("CodeSync IDE");
            assertThat(response.getOwnerId()).isEqualTo(OWNER_ID);
            verify(projectRepository).save(any(Project.class));
        }

        @Test
        @DisplayName("should default visibility to PRIVATE when not specified")
        void shouldDefaultToPrivate() {
            CreateProjectRequest req = new CreateProjectRequest();
            req.setName("Secret Project");
            req.setLanguage(Language.JAVA);
            req.setVisibility(null);   // not set

            Project saved = Project.builder().id(2L).name("Secret Project")
                    .language(Language.JAVA).visibility(Visibility.PRIVATE)
                    .ownerId(OWNER_ID).build();
            when(projectRepository.save(any(Project.class))).thenReturn(saved);

            ProjectResponse response = projectService.createProject(req, OWNER_ID);

            assertThat(response.getVisibility()).isEqualTo(Visibility.PRIVATE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── GET ─────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectById()")
    class GetProject {

        @Test
        @DisplayName("owner can read their own PRIVATE project")
        void ownerCanReadPrivateProject() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

            ProjectResponse response = projectService.getProjectById(PROJECT_ID, OWNER_ID);

            assertThat(response.getId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("non-member cannot read a PRIVATE project")
        void nonMemberCannotReadPrivateProject() {
            Project project = sampleProject(); // PRIVATE
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OTHER_ID)).thenReturn(false);

            assertThatThrownBy(() -> projectService.getProjectById(PROJECT_ID, OTHER_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("anyone can read a PUBLIC project")
        void anyoneCanReadPublicProject() {
            Project project = sampleProject();
            project.setVisibility(Visibility.PUBLIC);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

            assertThatNoException()
                    .isThrownBy(() -> projectService.getProjectById(PROJECT_ID, OTHER_ID));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown project")
        void throwsWhenProjectNotFound() {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectById(99L, OWNER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── UPDATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateProject()")
    class UpdateProject {

        @Test
        @DisplayName("owner can update project name")
        void ownerCanUpdateName() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateProjectRequest req = new UpdateProjectRequest();
            req.setName("Renamed Project");

            ProjectResponse response = projectService.updateProject(PROJECT_ID, req, OWNER_ID);

            assertThat(response.getName()).isEqualTo("Renamed Project");
        }

        @Test
        @DisplayName("non-member cannot update project")
        void nonMemberCannotUpdate() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OTHER_ID))
                    .thenReturn(Optional.empty());

            UpdateProjectRequest req = new UpdateProjectRequest();
            req.setName("Hacked Name");

            assertThatThrownBy(() -> projectService.updateProject(PROJECT_ID, req, OTHER_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── DELETE / ARCHIVE ────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteProject() & archiveProject()")
    class DeleteArchive {

        @Test
        @DisplayName("owner can delete project")
        void ownerCanDelete() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

            assertThatNoException()
                    .isThrownBy(() -> projectService.deleteProject(PROJECT_ID, OWNER_ID));
            verify(projectRepository).delete(project);
        }

        @Test
        @DisplayName("non-owner cannot delete project")
        void nonOwnerCannotDelete() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

            assertThatThrownBy(() -> projectService.deleteProject(PROJECT_ID, OTHER_ID))
                    .isInstanceOf(AccessDeniedException.class);
            verify(projectRepository, never()).delete(any());
        }

        @Test
        @DisplayName("owner can archive project")
        void ownerCanArchive() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            projectService.archiveProject(PROJECT_ID, OWNER_ID);

            assertThat(project.isArchived()).isTrue();
            verify(projectRepository).save(project);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── MEMBERS ─────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("addMember()")
    class AddMember {

        @Test
        @DisplayName("owner can add a member")
        void ownerCanAddMember() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OTHER_ID)).thenReturn(false);

            ProjectMember saved = ProjectMember.builder()
                    .id(1L).project(project).userId(OTHER_ID).role(MemberRole.DEVELOPER).build();
            when(memberRepository.save(any())).thenReturn(saved);

            AddMemberRequest req = new AddMemberRequest();
            req.setUserId(OTHER_ID);
            req.setRole(MemberRole.DEVELOPER);

            var response = projectService.addMember(PROJECT_ID, req, OWNER_ID);

            assertThat(response.getUserId()).isEqualTo(OTHER_ID);
            assertThat(response.getRole()).isEqualTo(MemberRole.DEVELOPER);
        }

        @Test
        @DisplayName("throws DuplicateResourceException if already a member")
        void throwsOnDuplicateMember() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OTHER_ID)).thenReturn(true);

            AddMemberRequest req = new AddMemberRequest();
            req.setUserId(OTHER_ID);
            req.setRole(MemberRole.GUEST);

            assertThatThrownBy(() -> projectService.addMember(PROJECT_ID, req, OWNER_ID))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("cannot add owner as a member")
        void cannotAddOwnerAsMember() {
            Project project = sampleProject();
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

            AddMemberRequest req = new AddMemberRequest();
            req.setUserId(OWNER_ID);  // owner trying to add themselves
            req.setRole(MemberRole.DEVELOPER);

            assertThatThrownBy(() -> projectService.addMember(PROJECT_ID, req, OWNER_ID))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("owner");
        }
    }
}
