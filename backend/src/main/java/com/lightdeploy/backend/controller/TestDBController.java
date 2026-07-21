package com.lightdeploy.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class TestDBController {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test-db")
    public List<Map<String, Object>> testDb() {
        return jdbcTemplate.queryForList("DESCRIBE deploy_records");
    }
}
