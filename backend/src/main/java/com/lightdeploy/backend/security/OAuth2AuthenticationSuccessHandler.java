package com.lightdeploy.backend.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.mapper.UserMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Autowired(required = false)
    private OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        Number gitlabIdNumber = (Number) attributes.get("id");
        Integer gitlabId = gitlabIdNumber != null ? gitlabIdNumber.intValue() : null;
        
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("gitlab_id", gitlabId));
        
        if (user != null) {
            // 同步 OAuth 授权（含 refresh token）：access token 默认 2 小时过期，
            // 靠 refresh_token + token_expires_at 做自动刷新，避免与 1 天的应用 JWT 脱节
            persistAuthorizedClient(request, authentication, user);
            String token = jwtUtils.generateToken(user.getId(), user.getUsername());
            String frontendUrl = frontendBaseUrl + "/login/success?token=" + token;
            getRedirectStrategy().sendRedirect(request, response, frontendUrl);
        } else {
            String frontendUrl = frontendBaseUrl + "/login?error=user_not_found";
            getRedirectStrategy().sendRedirect(request, response, frontendUrl);
        }
    }

    /**
     * 从本次登录的 authorized client 取出 token  trio 持久化。
     * 登录流程把 client 存到 session 级 repository（与 success handler 同请求），
     * 优先经 repository 读取，取不到再试 authorized client service；都没有则跳过
     * （老版本 GitLab 可能不下发 refresh token，后续走过期提示分支）。
     */
    private void persistAuthorizedClient(HttpServletRequest request, Authentication authentication, User user) {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
                return;
            }
            String registrationId = oauthToken.getAuthorizedClientRegistrationId();
            OAuth2AuthorizedClient client = null;
            if (authorizedClientRepository != null) {
                try {
                    client = authorizedClientRepository.loadAuthorizedClient(registrationId, authentication, request);
                } catch (Exception e) {
                    log.warn("Failed to load authorized client from repository: {}", e.getMessage());
                }
            }
            if (client == null && authorizedClientService != null) {
                try {
                    client = authorizedClientService.loadAuthorizedClient(registrationId, oauthToken.getName());
                } catch (Exception e) {
                    log.warn("Failed to load authorized client from service: {}", e.getMessage());
                }
            }
            if (client == null || client.getAccessToken() == null) {
                log.warn("No authorized client available at login success; skip token persistence");
                return;
            }
            user.setAccessToken(client.getAccessToken().getTokenValue());
            if (client.getAccessToken().getExpiresAt() != null) {
                user.setTokenExpiresAt(LocalDateTime.ofInstant(
                        client.getAccessToken().getExpiresAt(), ZoneId.systemDefault()));
            }
            if (client.getRefreshToken() != null) {
                user.setRefreshToken(client.getRefreshToken().getTokenValue());
            }
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        } catch (Exception e) {
            // token 持久化失败不阻断登录（CustomOAuth2UserService 已存 access token）
            log.warn("Failed to persist authorized client tokens: {}", e.getMessage());
        }
    }
}
