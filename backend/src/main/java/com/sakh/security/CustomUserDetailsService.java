package com.sakh.security;

import com.sakh.entity.User;
import com.sakh.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads user-specific data from the database for Spring Security authentication.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public CustomUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

		return org.springframework.security.core.userdetails.User.builder()
				.username(user.getEmail())
				.password(user.getPasswordHash())
				.authorities(mapAuthorities(user))
				.build();
	}

	private List<SimpleGrantedAuthority> mapAuthorities(User user) {
    return List.of(
        new SimpleGrantedAuthority("ROLE_" + user.getRole().getName())
    );
}
}
