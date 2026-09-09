package com.lightdeploy.backend.exception;

/**
 * GitLab OAuth 授权失效：access token 过期且无法自动刷新
 * （无 refresh token、刷新被拒、或 GitLab 未下发 refresh token）。
 * 由 GlobalExceptionHandler 统一映射为 HTTP 440 + GITLAB_TOKEN_EXPIRED，
 * 与应用自身会话过期（HTTP 401）严格区分，前端据此引导用户重新走 GitLab OAuth，
 * 而不是把用户踢到登录页。
 */
public class GitLabTokenExpiredException extends RuntimeException {

    public GitLabTokenExpiredException(String message) {
        super(message);
    }

    public GitLabTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
