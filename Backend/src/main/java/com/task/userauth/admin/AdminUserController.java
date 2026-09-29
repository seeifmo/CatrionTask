package com.task.userauth.admin;

import com.task.userauth.common.PageResponse;
import com.task.userauth.user.UserResponse;
import com.task.userauth.user.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints. Access is checked twice on purpose (defence in depth): by the URL rule in
 * SecurityConfig, and by {@code @PreAuthorize} here, so moving or copying a method can't silently
 * drop the check.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /** Lists all users, oldest first. Sorting is fixed so clients can't sort by internal columns. */
    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userService.listUsers(page, size);
    }
}
