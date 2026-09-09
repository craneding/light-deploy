package com.lightdeploy.backend.controller;

import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.exception.GitLabTokenExpiredException;
import com.lightdeploy.backend.mapper.UserMapper;
import com.lightdeploy.backend.service.GitLabTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/gitlab")
public class GitlabController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GitLabTokenService gitLabTokenService;

    @org.springframework.beans.factory.annotation.Value("${gitlab.url}")
    private String gitlabUrl;

    // Use RestTemplate or WebClient to call Gitlab API
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 带 GitLab token 自动刷新 + 401 单次重试的请求。
     * token 彻底失效时抛 GitLabTokenExpiredException（→ HTTP 440），
     * 与应用自身会话过期（HTTP 401）严格区分，避免前端误登出。
     */
    private <T> ResponseEntity<T> exchangeWithTokenRefresh(User user, String url,
                                                           ParameterizedTypeReference<T> responseType) {
        String token = gitLabTokenService.getValidAccessToken(user.getId());
        try {
            return restTemplate.exchange(url, HttpMethod.GET, authEntity(token), responseType);
        } catch (HttpClientErrorException.Unauthorized e) {
            if (isInvalidToken(e)) {
                // 过期竞态：检查时未过期、调用时刚过期 → 强制刷新后只重试一次
                String newToken = gitLabTokenService.refreshAccessToken(user.getId());
                return restTemplate.exchange(url, HttpMethod.GET, authEntity(newToken), responseType);
            }
            throw e;
        }
    }

    private boolean isInvalidToken(HttpClientErrorException.Unauthorized e) {
        String body = e.getResponseBodyAsString();
        return body != null && body.contains("invalid_token");
    }

    private HttpEntity<String> authEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private User currentUser() {
        Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new GitLabTokenExpiredException("GitLab 授权缺失，请重新登录后再试。");
        }
        return user;
    }

    @GetMapping("/projects")
    public ResponseEntity<?> getAccessibleProjects(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer per_page) {
        User user = currentUser();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(gitlabUrl + "/api/v4/projects")
                .queryParam("membership", "true")
                .queryParam("simple", "true")
                .queryParam("page", page)
                .queryParam("per_page", per_page);
        
        if (search != null && !search.isEmpty()) {
            builder.queryParam("search", search);
        }
        
        String gitlabApiUrl = builder.toUriString();
        
        try {
            ResponseEntity<List<Map<String, Object>>> response = exchangeWithTokenRefresh(
                    user,
                    gitlabApiUrl,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            // Extract total count from GitLab headers
            String totalStr = response.getHeaders().getFirst("X-Total");
            long total = totalStr != null ? Long.parseLong(totalStr) : 0L;
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("list", response.getBody());
            result.put("total", total);
            
            return ResponseEntity.ok(result);
        } catch (GitLabTokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching projects from GitLab: " + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/branches")
    public ResponseEntity<?> getBranches(@org.springframework.web.bind.annotation.PathVariable String projectId) {
        User user = currentUser();
        
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches";
        try {
            ResponseEntity<List<Map<String, Object>>> response = exchangeWithTokenRefresh(
                    user, url, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(response.getBody());
        } catch (GitLabTokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching branches: " + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/tags")
    public ResponseEntity<?> getTags(@org.springframework.web.bind.annotation.PathVariable String projectId) {
        User user = currentUser();
        
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/tags";
        try {
            ResponseEntity<List<Map<String, Object>>> response = exchangeWithTokenRefresh(
                    user, url, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(response.getBody());
        } catch (GitLabTokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching tags: " + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/commits")
    public ResponseEntity<?> getCommits(@org.springframework.web.bind.annotation.PathVariable String projectId,
                                        @org.springframework.web.bind.annotation.RequestParam(required = false) String ref_name) {
        User user = currentUser();
        
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/commits";
        if (ref_name != null && !ref_name.isEmpty()) {
            url += "?ref_name=" + ref_name;
        }
        try {
            ResponseEntity<List<Map<String, Object>>> response = exchangeWithTokenRefresh(
                    user, url, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(response.getBody());
        } catch (GitLabTokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching commits: " + e.getMessage());
        }
    }
}
