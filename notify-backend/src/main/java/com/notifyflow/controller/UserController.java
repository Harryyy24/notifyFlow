package com.notifyflow.controller;

import com.notifyflow.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "List registered users")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List all users", description = "Returns id, name, email, and role for every registered user")
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getRole().name()))
                .toList();
    }

    public record UserSummary(Long id, String name, String email, String role) {}
}
