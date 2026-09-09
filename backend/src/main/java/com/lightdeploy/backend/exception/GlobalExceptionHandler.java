package com.lightdeploy.backend.exception;

import com.lightdeploy.backend.exception.GitLabTokenExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * GitLab OAuth 授权失效：与应用自身会话过期（HTTP 401）严格区分，
     * 返回 440 + GITLAB_TOKEN_EXPIRED，前端引导用户重新走 GitLab OAuth，
     * 而不是清掉应用 token 跳登录页。
     */
    @ExceptionHandler(GitLabTokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleGitLabTokenExpired(GitLabTokenExpiredException e) {
        logger.warn("GitLab token expired: {}", e.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("code", 440);
        body.put("message", "GITLAB_TOKEN_EXPIRED");
        body.put("detail", e.getMessage());
        return ResponseEntity.status(440).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("Unhandled exception occurred", e);
        Map<String, Object> body = new HashMap<>();
        body.put("code", 500);
        body.put("message", e.getMessage() != null ? e.getMessage() : "Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
