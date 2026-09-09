package com.fitnesshub.user;

import com.fitnesshub.common.web.ApiResponse;
import com.fitnesshub.user.dto.UpdateUserRequest;
import com.fitnesshub.user.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        return ApiResponse.of(userMapper.toDto(userService.getCurrentUser()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserDto> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.of(userMapper.toDto(userService.updateCurrentUser(request)));
    }
}
