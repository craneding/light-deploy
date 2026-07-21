package com.lightdeploy.backend.controller;

import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo() {
        Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);
        
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }
        
        // Return user info without sensitive access tokens
        user.setAccessToken(null);
        return ResponseEntity.ok(user);
    }
}