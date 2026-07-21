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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

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
            String token = jwtUtils.generateToken(user.getId(), user.getUsername());
            String frontendUrl = frontendBaseUrl + "/login/success?token=" + token;
            getRedirectStrategy().sendRedirect(request, response, frontendUrl);
        } else {
            String frontendUrl = frontendBaseUrl + "/login?error=user_not_found";
            getRedirectStrategy().sendRedirect(request, response, frontendUrl);
        }
    }
}
