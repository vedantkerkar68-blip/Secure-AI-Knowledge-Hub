package com.sakh.service;

import java.time.Instant;

import com.sakh.entity.User;
import com.sakh.enums.UserStatus;
import com.sakh.exception.DuplicateResourceException;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.repository.UserRepository;
import com.sakh.security.JwtService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user registration and login business logic.
 */
@Service
public class AuthenticationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthenticationService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public String register(User user, String rawPassword) {
		if (userRepository.existsByEmail(user.getEmail())) {
			throw new DuplicateResourceException("User already exists with email: " + user.getEmail());
		}

		Instant now = Instant.now();
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setStatus(UserStatus.ACTIVE);
		user.setCreatedAt(now);
		user.setUpdatedAt(now);

		User savedUser = userRepository.save(user);
		return jwtService.generateToken(toUserDetails(savedUser));
	}

	public String login(String email, String rawPassword) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			throw new ResourceNotFoundException("Invalid email or password");
		}

		return jwtService.generateToken(toUserDetails(user));
	}

	private UserDetails toUserDetails(User user) {
		return org.springframework.security.core.userdetails.User.builder()
				.username(user.getEmail())
				.password(user.getPasswordHash())
				.authorities(new SimpleGrantedAuthority(user.getRole().getName()))
				.build();
	}
}
