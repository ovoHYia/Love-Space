package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.MeResponse;
import com.lovespace.api.dto.ApiDtos.CsrfResponse;
import com.lovespace.api.error.ApiException;
import com.lovespace.service.AccountService;
import com.lovespace.service.LoginAttemptService;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final AccountService accounts;
    private final LoginAttemptService loginAttempts;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    public AuthController(AuthenticationManager authenticationManager, AccountService accounts,
                          LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager; this.accounts = accounts; this.loginAttempts = loginAttempts;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken()); }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public MeResponse login(@RequestParam String username, @RequestParam String password,
                             HttpServletRequest request, HttpServletResponse response) {
        String normalizedUsername = username.trim();
        String remoteAddress = request.getRemoteAddr();
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
        } catch (AuthenticationException ex) {
            loginAttempts.failed(normalizedUsername, remoteAddress);
            log.info("Login failed for user {} from {}: {}", normalizedUsername, remoteAddress, ex.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null) log.info("Logout for user {}", authentication.getName());
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/me") public MeResponse me(Authentication authentication) { return accounts.me(authentication); }
}
