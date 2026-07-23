package com.hiresemble.auth.security;

import com.hiresemble.auth.api.dto.CurrentUserDto;
import com.hiresemble.auth.domain.model.UserRole;
import com.hiresemble.auth.domain.model.UserStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

public record AuthenticatedUser(
        UUID id, String email, String displayName, UserRole role, UserStatus status)
        implements Serializable, AuthenticatedPrincipal {

    @Serial private static final long serialVersionUID = 1L;

    public CurrentUserDto toDto() {
        return new CurrentUserDto(id, email, displayName);
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
