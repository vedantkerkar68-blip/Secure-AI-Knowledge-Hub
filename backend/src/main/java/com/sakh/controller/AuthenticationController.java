package com.sakh.controller;

import com.sakh.dto.auth.AuthResponse;
import com.sakh.dto.auth.LoginRequest;
import com.sakh.dto.auth.RegisterRequest;
import com.sakh.security.SakhUserDetails;
import com.sakh.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User registration and login endpoints")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns a JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "409", description = "User already exists")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authenticationService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates user credentials and returns a JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "404", description = "Invalid email or password")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes the current JWT token so it can no longer be used")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization,
                                       Authentication authentication) {
        String token = authorization.substring(7);
        Long userId = ((SakhUserDetails) authentication.getPrincipal()).getId();
        authenticationService.logout(token, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Rotates the current JWT token, revoking the old one and returning a fresh token")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authorization) {
        String token = authorization.substring(7);
        return ResponseEntity.ok(authenticationService.refresh(token));
    }
}
