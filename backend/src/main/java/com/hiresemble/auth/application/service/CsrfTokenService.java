package com.hiresemble.auth.application.service;

import com.hiresemble.auth.api.dto.CsrfDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class CsrfTokenService {

    private final CsrfTokenRepository tokenRepository;

    public CsrfTokenService(CsrfTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public CsrfDto current(CsrfToken token) {
        return new CsrfDto(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    public CsrfDto rotate(HttpServletRequest request, HttpServletResponse response) {
        tokenRepository.saveToken(null, request, response);
        CsrfToken token = tokenRepository.generateToken(request);
        tokenRepository.saveToken(token, request, response);
        return current(token);
    }
}
