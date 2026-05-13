package com.codesync.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

	private final StringRedisTemplate redisTemplate;

	public String createInviteToken(String value) {

		String token = UUID.randomUUID().toString();

		redisTemplate.opsForValue().set("invite:" + token, value, Duration.ofHours(24));

		return token;
	}

	public String getInviteData(String token) {
		return redisTemplate.opsForValue().get("invite:" + token);
	}

	public void removeInvite(String token) {
		redisTemplate.delete("invite:" + token);
	}
}