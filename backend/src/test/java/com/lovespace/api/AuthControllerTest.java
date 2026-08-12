package com.lovespace.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lovespace.api.error.ApiException;
import com.lovespace.service.AccountService;
import com.lovespace.service.LoginAttemptService;
import com.lovespace.service.PasswordResetAttemptService;
import com.lovespace.service.PasswordResetTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock AuthenticationManager authenticationManager;
    @Mock AccountService accounts;
    @Mock LoginAttemptService loginAttempts;
    @Mock PasswordResetAttemptService passwordResetAttempts;
    @Mock PasswordResetTokenService passwordResetTokens;

    @Test
    void authenticationInfrastructureFailureReturns503WithoutPasswordFailureCount() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new InternalAuthenticationServiceException("database unavailable"));
        AuthController controller = new AuthController(authenticationManager, accounts, loginAttempts,
                passwordResetAttempts, passwordResetTokens);
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        ApiException error = assertThrows(ApiException.class,
                () -> controller.login("alice", "alice-pass-123", request, response));

        assertEquals(503, error.getStatus().value());
        assertEquals("AUTHENTICATION_UNAVAILABLE", error.getCode());
        verify(loginAttempts, never()).failed(any(), any());
    }
}
