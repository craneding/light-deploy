package com.lightdeploy.backend.controller;

import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.mapper.UserMapper;
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

    @org.springframework.beans.factory.annotation.Value("${gitlab.url}")
    private String gitlabUrl;

    // Use RestTemplate or WebClient to call Gitlab API
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/projects")
    public ResponseEntity<?> getAccessibleProjects(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer per_page) {
        // Get user id from SecurityContext (set by JwtFilter)
        Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);

        if (user == null || user.getAccessToken() == null) {
            return ResponseEntity.status(400).body("User not authenticated or missing access token");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(gitlabUrl + "/api/v4/projects")
                .queryParam("membership", "true")
                .queryParam("simple", "true")
                .queryParam("page", page)
                .queryParam("per_page", per_page);
        
        if (search != null && !search.isEmpty()) {
            builder.queryParam("search", search);
        }
        
        String gitlabApiUrl = builder.toUriString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getAccessToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    gitlabApiUrl,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            // Extract total count from GitLab headers
            String totalStr = response.getHeaders().getFirst("X-Total");
            long total = totalStr != null ? Long.parseLong(totalStr) : 0L;
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("list", response.getBody());
            result.put("total", total);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching projects from GitLab: " + e.getMessage());
        }
    }

    private HttpEntity<String> getAuthEntity() {
        Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null || user.getAccessToken() == null) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getAccessToken());
        return new HttpEntity<>(headers);
    }

    @GetMapping("/projects/{projectId}/branches")
    public ResponseEntity<?> getBranches(@org.springframework.web.bind.annotation.PathVariable String projectId) {
        HttpEntity<String> entity = getAuthEntity();
        if (entity == null) return ResponseEntity.status(400).body("User not authenticated or missing access token");
        
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches";
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching branches: " + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/tags")
    public ResponseEntity<?> getTags(@org.springframework.web.bind.annotation.PathVariable String projectId) {
        HttpEntity<String> entity = getAuthEntity();
        if (entity == null) return ResponseEntity.status(400).body("User not authenticated or missing access token");
        
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/tags";
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching tags: " + e.getMessage());
        }
    }

    @GetMapping("/projects/{projectId}/commits")
    public ResponseEntity<?> getCommits(@org.springframework.web.bind.annotation.PathVariable String projectId,
                                        @org.springframework.web.bind.annotation.RequestParam(required = false) String ref_name) {
        HttpEntity<String> entity = getAuthEntity();
        if (entity == null) return ResponseEntity.status(400).body("User not authenticated or missing access token");
        
        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/commits";
        if (ref_name != null && !ref_name.isEmpty()) {
            url += "?ref_name=" + ref_name;
        }
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching commits: " + e.getMessage());
        }
    }
}
