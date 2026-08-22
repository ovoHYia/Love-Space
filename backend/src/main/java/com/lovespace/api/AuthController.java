package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.MeResponse;
import com.lovespace.api.dto.ApiDtos.CsrfResponse;
import com.lovespace.api.dto.ApiDtos.PasswordResetRequest;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.AccountService;
import com.lovespace.service.LoginAttemptService;
import com.lovespace.service.PasswordResetAttemptService;
import com.lovespace.service.PasswordResetTokenService;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,50}$");
    private final AuthenticationManager authenticationManager;
    private final AccountService accounts;
    private final LoginAttemptService loginAttempts;
    private final PasswordResetAttemptService passwordResetAttempts;
    private final PasswordResetTokenService passwordResetTokens;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    public AuthController(AuthenticationManager authenticationManager, AccountService accounts,
                          LoginAttemptService loginAttempts,
                          PasswordResetAttemptService passwordResetAttempts,
                          PasswordResetTokenService passwordResetTokens) {
        this.authenticationManager = authenticationManager; this.accounts = accounts; this.loginAttempts = loginAttempts;
        this.passwordResetAttempts = passwordResetAttempts; this.passwordResetTokens = passwordResetTokens;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        String username = request.username().trim();
        String remoteAddress = servletRequest.getRemoteAddr();
        passwordResetAttempts.requireAllowed(username, remoteAddress);
        if (!passwordResetTokens.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PASSWORD_RESET_DISABLED",
                    "服务器未配置恢复口令，请联系管理员设置 PASSWORD_RESET_TOKEN");
        }
        if (!passwordResetTokens.matches(request.recoveryToken())) {
            passwordResetAttempts.failed(username, remoteAddress);
            throw passwordResetFailed();
        }
        try {
            accounts.resetPassword(username, request.newPassword());
            passwordResetAttempts.succeeded(username, remoteAddress);
        } catch (ApiException ex) {
            if ("PASSWORD_RESET_FAILED".equals(ex.getCode())) {
                passwordResetAttempts.failed(username, remoteAddress);
                throw passwordResetFailed();
            }
            throw ex;
        }
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken()); }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public MeResponse login(@RequestParam String username, @RequestParam String password,
                             HttpServletRequest request, HttpServletResponse response) {
        String normalizedUsername = username.trim();
        String remoteAddress = request.getRemoteAddr();
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()
                || password.isEmpty()
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        loginAttempts.requireAllowed(normalizedUsername, remoteAddress);
        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, password));
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) oldSession.invalidate();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(result);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            loginAttempts.succeeded(normalizedUsername, remoteAddress);
            log.info("Login succeeded for user {} from {}", normalizedUsername, remoteAddress);
            return accounts.me(result);
        } catch (InternalAuthenticationServiceException ex) {
            log.error("Authentication infrastructure failed for {} from {}", normalizedUsername, remoteAddress, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTHENTICATION_UNAVAILABLE",
                    "登录服务暂时不可用，请稍后重试");
        } catch (BadCredentialsException ex) {
            loginAttempts.failed(normalizedUsername, remoteAddress);
            log.info("Login failed for user {} from {}: {}", normalizedUsername, remoteAddress, ex.getMessage());
            throw invalidCredentials();
        } catch (AuthenticationException ex) {
            // 账号已禁用、认证配置错误等不是密码错误，不应消耗密码失败额度。
            log.warn("Authentication rejected for {} without counting a password failure: {}",
                    normalizedUsername, ex.getClass().getSimpleName());
            throw invalidCredentials();
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null) log.info("Logout for user {}", authentication.getName());
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/me") public MeResponse me(Authentication authentication) { return accounts.me(authentication); }

    private ApiException passwordResetFailed() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_FAILED", "账号或恢复口令不正确");
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
    }
}
