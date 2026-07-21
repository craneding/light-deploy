package com.lightdeploy.backend.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        // Process user info and save to database
        processOAuth2User(userRequest, oAuth2User);
        
        return oAuth2User;
    }

    private void processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        // GitLab specific attributes
        Number gitlabIdNumber = (Number) attributes.get("id");
        Integer gitlabId = gitlabIdNumber != null ? gitlabIdNumber.intValue() : null;
        String username = (String) attributes.get("username");
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");
        String accessToken = userRequest.getAccessToken().getTokenValue();

        User existingUser = userMapper.selectOne(new QueryWrapper<User>().eq("gitlab_id", gitlabId));

        if (existingUser != null) {
            existingUser.setUsername(username);
            existingUser.setEmail(email);
            existingUser.setAvatarUrl(avatarUrl);
            existingUser.setAccessToken(accessToken);
            existingUser.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(existingUser);
        } else {
            User newUser = new User();
            newUser.setGitlabId(gitlabId);
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setAvatarUrl(avatarUrl);
            newUser.setAccessToken(accessToken);
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(newUser);
        }
    }
}
