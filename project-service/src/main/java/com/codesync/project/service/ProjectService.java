package com.codesync.project.service;

import com.codesync.project.dto.*;
import com.codesync.project.exception.AccessDeniedException;
import com.codesync.project.exception.DuplicateResourceException;
import com.codesync.project.exception.ResourceNotFoundException;
import com.codesync.project.model.*;
import com.codesync.project.repository.ProjectMemberRepository;
import com.codesync.project.repository.ProjectRepository;
import com.codesync.project.service.InvitationProducer;
import com.codesync.project.service.InvitationService;
import com.codesync.project.repository.ProjectStarRepository;
import com.codesync.project.dto.InviteMemberRequest;
import com.codesync.project.dto.InvitationMailEvent;
import com.codesync.project.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Core business logic for project management.
 *
 * Authorization model: - Only the owner can delete or archive a project. -
 * Owner or ADMIN member can update project metadata. - Owner or ADMIN member
 * can add / remove members. - Any authenticated user can view PUBLIC projects.
 * - Only owner or members can view PRIVATE projects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository memberRepository;
	private final InvitationService invitationService;
	private final InvitationProducer invitationProducer;
	private final NotificationProducer notificationProducer;
	private final ProjectStarRepository projectStarRepository;

	// ═══════════════════════════════════════════════════════════════════════
	// ── Project CRUD ────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/**
	 * Create a new project owned by the calling user.
	 */
	public ProjectResponse createProject(CreateProjectRequest req, Long ownerId) {
		log.info("User {} creating project '{}'", ownerId, req.getName());

		Project project = Project.builder().name(req.getName()).description(req.getDescription())
				.language(req.getLanguage())
				.visibility(req.getVisibility() != null ? req.getVisibility() : Visibility.PRIVATE).ownerId(ownerId)
				.build();

		project = projectRepository.save(project);
		log.info("Project created with id={}", project.getId());
		return toResponse(project);
	}

	/**
	 * Get a single project by ID – checks read access.
	 */
	@Transactional(readOnly = true)
	public ProjectResponse getProjectById(Long projectId, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertReadAccess(project, requestingUserId);
		return toResponse(project);
	}

	/**
	 * All projects accessible by the requesting user (owned + member of).
	 */
	@Transactional(readOnly = true)
	public Page<ProjectResponse> getMyProjects(Long userId, Pageable pageable) {
		return projectRepository.findAccessibleByUser(userId, pageable).map(this::toResponse);
	}

	/**
	 * Search non-archived projects by name keyword.
	 */
	@Transactional(readOnly = true)
	public Page<ProjectResponse> searchProjects(String keyword, Long userId, Pageable pageable) {
		return projectRepository.searchByKeyword(keyword, userId, pageable).map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public Page<ProjectResponse> getPublicProjects(Pageable pageable) {
		return projectRepository.findByVisibilityAndArchivedFalseOrderByCreatedAtDesc(Visibility.PUBLIC, pageable)
				.map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public Page<ProjectResponse> searchPublicProjects(String keyword, Pageable pageable) {
		return projectRepository.searchPublicProjects(keyword, pageable).map(this::toResponse);
	}

	/**
	 * Partially update a project's metadata. Only owner or ADMIN member allowed.
	 */
	public ProjectResponse updateProject(Long projectId, UpdateProjectRequest req, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertWriteAccess(project, requestingUserId);

		if (req.getName() != null)
			project.setName(req.getName());
		if (req.getDescription() != null)
			project.setDescription(req.getDescription());
		if (req.getLanguage() != null)
			project.setLanguage(req.getLanguage());
		if (req.getVisibility() != null)
			project.setVisibility(req.getVisibility());

		project = projectRepository.save(project);
		log.info("Project {} updated by user {}", projectId, requestingUserId);
		return toResponse(project);
	}

	/**
	 * Archive (soft-delete) a project. Only the owner can do this.
	 */
	public void archiveProject(Long projectId, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertOwner(project, requestingUserId);
		project.setArchived(true);
		projectRepository.save(project);
		log.info("Project {} archived by owner {}", projectId, requestingUserId);
	}

	/**
	 * Permanently delete a project. Only the owner can do this.
	 */
	public void deleteProject(Long projectId, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertOwner(project, requestingUserId);
		projectRepository.delete(project);
		log.info("Project {} permanently deleted by owner {}", projectId, requestingUserId);
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── Member Management ───────────────────────────────────────────════════
	// ═══════════════════════════════════════════════════════════════════════

	/**
	 * Add a collaborator to a project. Only owner or ADMIN member can do this.
	 */
	public MemberResponse addMember(Long projectId, AddMemberRequest req, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertManageAccess(project, requestingUserId);

		// Cannot add the owner as a member
		if (req.getUserId().equals(project.getOwnerId())) {
			throw new DuplicateResourceException("User " + req.getUserId() + " is already the owner of this project");
		}

		// Prevent duplicate membership
		if (memberRepository.existsByProjectIdAndUserId(projectId, req.getUserId())) {
			throw new DuplicateResourceException(
					"User " + req.getUserId() + " is already a member of project " + projectId);
		}

		ProjectMember member = ProjectMember.builder().project(project).userId(req.getUserId()).role(req.getRole())
				.build();

		member = memberRepository.save(member);
		log.info("User {} added to project {} with role {}", req.getUserId(), projectId, req.getRole());
		return toMemberResponse(member);
	}

	/**
	 * List all members of a project. Any project member or owner can call this.
	 */
	@Transactional(readOnly = true)
	public List<MemberResponse> listMembers(Long projectId, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertReadAccess(project, requestingUserId);

		return memberRepository.findByProjectId(projectId).stream().map(this::toMemberResponse).toList();
	}

	/**
	 * Update a member's role. Only owner or ADMIN can do this.
	 */
	public MemberResponse updateMemberRole(Long projectId, Long memberId, MemberRole newRole, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);
		assertManageAccess(project, requestingUserId);

		ProjectMember member = memberRepository.findById(memberId).filter(m -> m.getProject().getId().equals(projectId))
				.orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

		member.setRole(newRole);
		member = memberRepository.save(member);
		log.info("Member {} role updated to {} in project {}", memberId, newRole, projectId);
		return toMemberResponse(member);
	}

	/**
	 * Remove a member from a project. Only owner or ADMIN can do this. A member can
	 * also remove themselves (leave the project).
	 */
	public void removeMember(Long projectId, Long userId, Long requestingUserId) {
		Project project = findProjectOrThrow(projectId);

		boolean isSelf = userId.equals(requestingUserId);
		boolean canManage = project.getOwnerId().equals(requestingUserId) || isAdminMember(projectId, requestingUserId);

		if (!isSelf && !canManage) {
			throw new AccessDeniedException("You do not have permission to remove members from this project");
		}

		memberRepository.deleteByProjectIdAndUserId(projectId, userId);
		log.info("User {} removed from project {} by user {}", userId, projectId, requestingUserId);
	}

	public void inviteMember(Long projectId, InviteMemberRequest req, Long requestingUserId) {

		Project project = findProjectOrThrow(projectId);
		assertManageAccess(project, requestingUserId);

		String value = projectId + "," + req.getEmail() + "," + req.getRole();

		String token = invitationService.createInviteToken(value);

		InvitationMailEvent event = InvitationMailEvent.builder()
		        .toEmail(req.getEmail())
		        .projectName(project.getName())
		        .role(req.getRole().name())
		        .invitedBy("CodeSync Owner")
		        .inviteLink("http://localhost:5173/invite/" + token)
		        .recipientId(req.getUserId())
		        .projectId(project.getId())
		        .build();

		invitationProducer.sendInvitation(event);

		log.info("Invitation sent to {}", req.getEmail());
	}

	@Transactional
	public String acceptInvitation(String token, Long userId) {

	    String data = invitationService.getInviteData(token);

	    if (data == null) {
	        throw new RuntimeException("Invitation expired or invalid");
	    }

	    String[] parts = data.split(",");

	    Long projectId = Long.parseLong(parts[0]);
	    String role = parts[2];

	    Project project = findProjectOrThrow(projectId);

	    boolean alreadyMember = memberRepository
	            .findByProjectIdAndUserId(projectId, userId)
	            .isPresent();

	    if (!alreadyMember) {
	        ProjectMember member = ProjectMember.builder()
	                .project(project)
	                .userId(userId)
	                .role(MemberRole.valueOf(role))
	                .build();

	        memberRepository.save(member);
	        
	        CreateNotificationRequest request = CreateNotificationRequest.builder()
	                .recipientId(project.getOwnerId())
	                .actorId(userId)
	                .type("PROJECT")
	                .title("Invitation Accepted")
	                .message("User " + userId + " joined project " + project.getName())
	                .build();

	        notificationProducer.send(request);
	    }

	    invitationService.removeInvite(token);

	    return "Invitation accepted successfully";
	}
	
	public String rejectInvitation(String token) {

	    String data = invitationService.getInviteData(token);

	    if (data == null) {
	        throw new RuntimeException("Invitation expired or invalid");
	    }

	    invitationService.removeInvite(token);

	    return "Invitation rejected";
	}
	
	// ═══════════════════════════════════════════════════════════════════════
	// ── Private Helpers ─────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	private Project findProjectOrThrow(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
	}

	/** Read access: owner, any member, or PUBLIC project. */
	private void assertReadAccess(Project project, Long userId) {
		if (project.getVisibility() == Visibility.PUBLIC)
			return;
		if (project.getOwnerId().equals(userId))
			return;
		if (memberRepository.existsByProjectIdAndUserId(project.getId(), userId))
			return;
		throw new AccessDeniedException("You do not have access to project " + project.getId());
	}

	/** Write access: owner or ADMIN member. */
	private void assertWriteAccess(Project project, Long userId) {

		// Owner → full access
		if (project.getOwnerId().equals(userId))
			return;

		// Member roles
		var member = memberRepository.findByProjectIdAndUserId(project.getId(), userId);

		if (member.isPresent()) {
			MemberRole role = member.get().getRole();

			// DEVELOPER and ADMIN can edit
			if (role == MemberRole.DEVELOPER || role == MemberRole.ADMIN) {
				return;
			}
		}

		throw new AccessDeniedException("You do not have permission to modify project " + project.getId());
	}

	/** Only the owner can delete / archive. */
	private void assertOwner(Project project, Long userId) {
		if (!project.getOwnerId().equals(userId)) {
			throw new AccessDeniedException("Only the project owner can perform this action");
		}
	}

	private boolean isAdminMember(Long projectId, Long userId) {
		return memberRepository.findByProjectIdAndUserId(projectId, userId).map(m -> m.getRole() == MemberRole.ADMIN)
				.orElse(false);
	}

	private void assertManageAccess(Project project, Long userId) {

		// Owner
		if (project.getOwnerId().equals(userId))
			return;

		// ADMIN member
		if (isAdminMember(project.getId(), userId))
			return;

		throw new AccessDeniedException("You do not have permission to manage members in project " + project.getId());
	}

	// ── Mapper helpers ─────────────────────────────────────────────────────

	private ProjectResponse toResponse(Project p) {
		return ProjectResponse.builder().id(p.getId()).name(p.getName()).description(p.getDescription())
				.language(p.getLanguage()).visibility(p.getVisibility()).ownerId(p.getOwnerId())
				.memberCount(p.getMembers().size()).archived(p.isArchived()).createdAt(p.getCreatedAt())
				.updatedAt(p.getUpdatedAt()).build();
	}

	private MemberResponse toMemberResponse(ProjectMember m) {
		return MemberResponse.builder().id(m.getId()).projectId(m.getProject().getId()).userId(m.getUserId())
				.role(m.getRole()).joinedAt(m.getJoinedAt()).build();
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── Phase 2 Features ────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	public ProjectResponse forkProject(Long projectId, Long userId) {
		Project original = findProjectOrThrow(projectId);
		assertReadAccess(original, userId);

		Project forked = Project.builder().name(original.getName() + " (Fork)").description(original.getDescription())
				.language(original.getLanguage()).visibility(Visibility.PRIVATE).ownerId(userId)
				.forkedFromProjectId(original.getId()).build();

		forked = projectRepository.save(forked);
		log.info("Project {} forked into {} by user {}", original.getId(), forked.getId(), userId);
		return toResponse(forked);
	}


	public void starProject(Long projectId, Long userId) {
		Project project = findProjectOrThrow(projectId);
		assertReadAccess(project, userId);

		if (!projectStarRepository.existsByProjectIdAndUserId(projectId, userId)) {
			projectStarRepository
					.save(com.codesync.project.model.ProjectStar.builder().project(project).userId(userId).build());
		}
	}

	public void unstarProject(Long projectId, Long userId) {
		projectStarRepository.findByProjectIdAndUserId(projectId, userId).ifPresent(projectStarRepository::delete);
	}

	@Transactional(readOnly = true)
	public ProjectAnalyticsResponse getAnalytics(Long projectId, Long userId) {
		Project project = findProjectOrThrow(projectId);
		assertReadAccess(project, userId);

		long members = memberRepository.findByProjectId(projectId).size() + 1; // +1 for owner
		long stars = projectStarRepository.countByProjectId(projectId);
		long forks = projectRepository.countByForkedFromProjectId(projectId);
		long ageInDays = java.time.temporal.ChronoUnit.DAYS.between(project.getCreatedAt(),
				java.time.LocalDateTime.now());

		return ProjectAnalyticsResponse.builder().projectId(projectId).totalMembers(members).totalStars(stars)
				.totalForks(forks).ageInDays(ageInDays).build();
	}
}
