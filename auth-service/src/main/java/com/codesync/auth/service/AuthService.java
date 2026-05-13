package com.codesync.auth.service;

import com.codesync.auth.dto.AuthResponse;
import com.codesync.auth.exception.DuplicateResourceException;
import com.codesync.auth.exception.ResourceNotFoundException;
import com.codesync.auth.model.BlacklistedToken;
import com.codesync.auth.model.RefreshToken;
import com.codesync.auth.model.Role;
import com.codesync.auth.model.User;
import com.codesync.auth.repository.BlacklistedTokenRepository;
import com.codesync.auth.repository.RefreshTokenRepository;
import com.codesync.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final BlacklistedTokenRepository blacklistedTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthResponse register(String username, String email, String password, String fullName) {
		log.info("Attempting to register user with email: {}", email);

		if (userRepository.existsByEmail(email)) {
			throw new DuplicateResourceException("Email '" + email + "' is already registered");
		}

		if (userRepository.existsByUsername(username)) {
			throw new DuplicateResourceException("Username '" + username + "' is already taken");
		}

		User user = User.builder().username(username).email(email).password(passwordEncoder.encode(password))
				.fullName(fullName).role(Role.USER).enabled(true).build();

		userRepository.save(user);

		log.info("User registered successfully with id: {}", user.getId());

		return createTokens(user);
	}

	public AuthResponse login(String email, String password) {
		log.info("Login attempt for email: {}", email);

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

		if (!user.isEnabled()) {
			throw new BadCredentialsException("Account is deactivated");
		}

		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new BadCredentialsException("Invalid email or password");
		}

		log.info("User '{}' logged in successfully", user.getUsername());

		return createTokens(user);
	}

	@Transactional(readOnly = true)
	public User getUserById(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
	}

	public User updateProfile(Long userId, String fullName, String username) {
		User user = getUserById(userId);

		if (username != null && !username.equals(user.getUsername())) {
			if (userRepository.existsByUsername(username)) {
				throw new DuplicateResourceException("Username '" + username + "' is already taken");
			}
			user.setUsername(username);
		}

		if (fullName != null) {
			user.setFullName(fullName);
		}

		return userRepository.save(user);
	}

	public void changePassword(Long userId, String oldPassword, String newPassword) {
		User user = getUserById(userId);

		if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
			throw new BadCredentialsException("Incorrect current password");
		}

		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
	}

	public void deactivateAccount(Long userId) {
		User user = getUserById(userId);
		user.setEnabled(false);
		userRepository.save(user);
		refreshTokenRepository.deleteByUser(user);
	}

	@Transactional(readOnly = true)
	public Page<User> searchUsers(String query, Pageable pageable) {
		return userRepository.searchUsers(query, pageable);
	}

	public AuthResponse refreshToken(String token) {
		RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

		if (refreshToken.isExpired()) {
			refreshTokenRepository.delete(refreshToken);
			throw new BadCredentialsException("Refresh token expired");
		}

		User user = refreshToken.getUser();

		if (!user.isEnabled()) {
			throw new BadCredentialsException("Account is deactivated");
		}

		String accessToken = jwtService.generateToken(user);

		return AuthResponse.builder().accessToken(accessToken).refreshToken(token).build();
	}

	public void logout(String accessToken, String refreshTokenStr) {
		if (accessToken != null && accessToken.startsWith("Bearer ")) {
			accessToken = accessToken.substring(7);
		}

		if (accessToken != null && jwtService.isTokenValid(accessToken)) {
			Date expiration = jwtService.getExpiration(accessToken);
			LocalDateTime expiryDate = expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

			if (!blacklistedTokenRepository.existsByToken(accessToken)) {
				blacklistedTokenRepository
						.save(BlacklistedToken.builder().token(accessToken).expiresAt(expiryDate).build());
			}
		}

		if (refreshTokenStr != null) {
			refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(refreshTokenRepository::delete);
		}
	}

	public boolean isTokenBlacklisted(String token) {
		return blacklistedTokenRepository.existsByToken(token);
	}

	// ================= ADMIN METHODS =================

	@Transactional(readOnly = true)
	public Page<User> getAllUsers(Pageable pageable) {
		return userRepository.findAllByOrderByIdDesc(pageable);
	}

	@Transactional(readOnly = true)
	public Map<String, Long> getAdminStats() {
		long total = userRepository.count();
		long active = userRepository.countByEnabled(true);
		long inactive = total - active;

		return Map.of("totalUsers", total, "activeUsers", active, "inactiveUsers", inactive);
	}

	public void activateUser(Long userId) {
		User user = getUserById(userId);
		user.setEnabled(true);
		userRepository.save(user);
	}

	public void deactivateUserByAdmin(Long userId) {
		User user = getUserById(userId);
		user.setEnabled(false);
		userRepository.save(user);
		refreshTokenRepository.deleteByUser(user);
	}

	public void deleteUser(Long userId) {
		User user = getUserById(userId);
		refreshTokenRepository.deleteByUser(user);
		userRepository.delete(user);
	}

	// ================= TOKEN CREATION =================

	private AuthResponse createTokens(User user) {
		String accessToken = jwtService.generateToken(user);

		refreshTokenRepository.deleteByUser(user);

		RefreshToken refreshToken = RefreshToken.builder().user(user).token(UUID.randomUUID().toString())
				.expiryDate(LocalDateTime.now().plusDays(7)).build();

		refreshTokenRepository.save(refreshToken);

		return AuthResponse.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken.getToken())
				.id(user.getId())
				.username(user.getUsername())
				.email(user.getEmail())
				.fullName(user.getFullName())
				.role(user.getRole().name())
				.premium(user.isPremium()).build();
	}
}
