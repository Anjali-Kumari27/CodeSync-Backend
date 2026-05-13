package com.codesync.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

	private String accessToken;

	private String refreshToken;

	private Long id;

	private String username;

	private String email;

	private String fullName;

	private String role;

	private boolean premium;
}