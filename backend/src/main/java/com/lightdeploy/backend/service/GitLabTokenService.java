package com.lightdeploy.backend.service;

import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.exception.GitLabTokenExpiredException;
import com.lightdeploy.backend.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitLab OAuth token 管理：调用 GitLab API 前统一经此取可用 token。
 * <p>
 * 背景：GitLab access token 默认 2 小时过期，而应用自身 JWT 为 1 天。
 * 登录时持久化的 token 若直接使用，2 小时后所有 GitLab 调用都会 401 invalid_token。
 * 本服务在过期前（5 分钟提前量）用 refresh_token 自动换新并持久化；
 * 无 refresh token 或刷新失败时抛 {@link GitLabTokenExpiredException}，
 * 由前端引导用户重新走 GitLab OAuth，而不是误判为应用会话过期。
 */
@Service
public class GitLabTokenService {

    private static final Logger log = LoggerFactory.getLogger(GitLabTokenService.class);

    /** 过期提前量：token 在过期前这么久即视为需刷新，覆盖时钟偏差 */
    private static final long REFRESH_BEFORE_EXPIRY_SECONDS = 300;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Value("${gitlab.url}")
    private String gitlabUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 按用户单飞刷新，避免并发重复换 token */
    private final ConcurrentHashMap<Integer, Object> refreshLocks = new ConcurrentHashMap<>();

    /**
     * 返回指定用户当前可用的 GitLab access token，临期自动刷新。
     *
     * @throws GitLabTokenExpiredException token 缺失/过期且无法刷新时
     */
    public String getValidAccessToken(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getAccessToken() == null || user.getAccessToken().isEmpty()) {
            throw new GitLabTokenExpiredException("GitLab 授权缺失，请重新登录后再试。");
        }
        if (isExpiringSoon(user)) {
            return refreshAccessToken(user.getId());
        }
        return user.getAccessToken();
    }

    /**
     * 强制用 refresh_token 换新并持久化，返回新 access token。
     *
     * @throws GitLabTokenExpiredException 无 refresh token 或刷新失败时
     */
    public String refreshAccessToken(Integer userId) {
        Object lock = refreshLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            User user = userMapper.selectById(userId);
            if (user == null || user.getAccessToken() == null) {
                throw new GitLabTokenExpiredException("GitLab 授权缺失，请重新登录后再试。");
            }
            // 双重检查：等待锁期间可能已被其他线程刷新
            if (!isExpiringSoon(user)) {
                return user.getAccessToken();
            }
            if (user.getRefreshToken() == null || user.getRefreshToken().isEmpty()) {
                throw new GitLabTokenExpiredException("GitLab 授权已过期，请重新登录后再试。");
            }

            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("gitlab");
            if (registration == null) {
                throw new GitLabTokenExpiredException("GitLab OAuth 配置缺失，无法刷新授权，请联系管理员。");
            }

            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", "refresh_token");
                form.add("refresh_token", user.getRefreshToken());
                form.add("client_id", registration.getClientId());
                form.add("client_secret", registration.getClientSecret());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        gitlabUrl + "/oauth/token",
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                Map<String, Object> body = response.getBody();
                if (body == null || !body.containsKey("access_token")) {
                    throw new GitLabTokenExpiredException("GitLab 授权刷新失败（响应异常），请重新登录后再试。");
                }

                String newAccessToken = (String) body.get("access_token");
                user.setAccessToken(newAccessToken);
                // GitLab 刷新时可能轮换 refresh_token；未返回则保留旧的
                if (body.get("refresh_token") instanceof String newRefresh && !newRefresh.isEmpty()) {
                    user.setRefreshToken(newRefresh);
                }
                Number expiresIn = (Number) body.get("expires_in");
                if (expiresIn != null) {
                    user.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn.longValue()));
                } else {
                    user.setTokenExpiresAt(null);
                }
                user.setUpdatedAt(LocalDateTime.now());
                userMapper.updateById(user);
                log.info("Refreshed GitLab access token for user {}", user.getUsername());
                return newAccessToken;
            } catch (GitLabTokenExpiredException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to refresh GitLab token for user {}: {}", user.getUsername(), e.getMessage());
                throw new GitLabTokenExpiredException("GitLab 授权已过期，请重新登录后再试。", e);
            }
        }
    }

    /** 未记录过期时间（老数据/老版本 GitLab）视为暂可用，401 时再触发刷新重试 */
    private boolean isExpiringSoon(User user) {
        if (user.getTokenExpiresAt() == null) {
            return false;
        }
        return !user.getTokenExpiresAt().isAfter(LocalDateTime.now().plusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS));
    }
}
