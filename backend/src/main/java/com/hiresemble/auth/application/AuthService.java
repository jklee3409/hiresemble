package com.hiresemble.auth.application;

import com.hiresemble.auth.api.AuthSessionDto;
import com.hiresemble.auth.api.LoginRequest;
import com.hiresemble.auth.api.SignupRequest;
import com.hiresemble.auth.domain.UserStatus;
import com.hiresemble.auth.infrastructure.UserEntity;
import com.hiresemble.auth.infrastructure.UserRepository;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.agentrun.application.AiPreferenceRegistrationService;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.profile.application.ProfileRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRegistrationService profileRegistrationService;
    private final AiPreferenceRegistrationService aiPreferenceRegistrationService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenService csrfTokenService;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            ProfileRegistrationService profileRegistrationService,
            AiPreferenceRegistrationService aiPreferenceRegistrationService,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository,
            CsrfTokenService csrfTokenService) {
        this.userRepository = userRepository;
        this.profileRegistrationService = profileRegistrationService;
        this.aiPreferenceRegistrationService = aiPreferenceRegistrationService;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenService = csrfTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthSessionDto signup(
            SignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String normalizedEmail = normalizeEmail(signupRequest.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Instant now = Instant.now();
        UserEntity user = UserEntity.create(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(signupRequest.password()),
                signupRequest.displayName().trim(),
                now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, exception);
        }
        profileRegistrationService.createDefaultProfile(user.id(), now);
        aiPreferenceRegistrationService.createDefaultPreference(user.id(), now);

        return establishAuthenticatedSession(user, request, response);
    }

    @Transactional
    public AuthSessionDto login(
            LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String normalizedEmail = normalizeEmail(loginRequest.email());
        UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);
        String hashToCheck = user == null ? dummyPasswordHash : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(loginRequest.password(), hashToCheck);
        if (user == null || !passwordMatches || user.status() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.recordLogin(Instant.now());
        userRepository.flush();
        return establishAuthenticatedSession(user, request, response);
    }

    public void logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void authenticate(
            AuthenticatedUser principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private AuthenticatedUser principal(UserEntity user) {
        return new AuthenticatedUser(
                user.id(), user.email(), user.displayName(), user.role(), user.status());
    }

    private AuthSessionDto establishAuthenticatedSession(
            UserEntity user, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser principal = principal(user);
        try {
            authenticate(principal, request, response);
            return new AuthSessionDto(
                    principal.toDto(), csrfTokenService.rotate(request, response));
        } catch (RuntimeException exception) {
            discardFailedAuthentication(request, exception);
            throw exception;
        }
    }

    private void discardFailedAuthentication(
            HttpServletRequest request, RuntimeException originalFailure) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
