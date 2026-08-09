package com.hiresemble.common.security;

import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.auth.security.WithdrawnUserFilter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseWriter errorWriter,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            WithdrawnUserFilter withdrawnUserFilter)
            throws Exception {
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                errorWriter.write(
                        request,
                        response,
                        exception instanceof CsrfException
                                ? ErrorCode.CSRF_INVALID
                                : ErrorCode.ACCESS_DENIED);

        http.securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/github-app-connections/setup/callback",
                                "/api/v1/github-app-connections/oauth/callback")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(
                                request, response, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        http.addFilterAfter(withdrawnUserFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    WithdrawnUserFilter withdrawnUserFilter(
            JdbcClient jdbc, SecurityErrorResponseWriter errorWriter) {
        return new WithdrawnUserFilter(jdbc, errorWriter);
    }

    @Bean
    FilterRegistrationBean<WithdrawnUserFilter> withdrawnUserFilterRegistration(
            WithdrawnUserFilter filter) {
        FilterRegistrationBean<WithdrawnUserFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:SESSION}") String name,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:Lax}") String sameSite) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(name);
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);
        return serializer;
    }

    @Bean("springSessionTransactionOperations")
    TransactionOperations springSessionTransactionOperations(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }
}
