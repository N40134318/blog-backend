package space.rainstorm.blogbackend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.dto.LoginRequest;
import space.rainstorm.blogbackend.dto.RegisterRequest;
import space.rainstorm.blogbackend.entity.User;
import space.rainstorm.blogbackend.repository.UserRepository;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.util.Map;

@RestController
@CrossOrigin(origins = "https://dev.rainstorm.space")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/api/register")
    public ApiResponse<Map<String, String>> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return new ApiResponse<>(400, "用户名不能为空", null);
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return new ApiResponse<>(400, "密码不能为空", null);
        }

        User existsUser = userRepository.findByUsername(request.getUsername());
        if (existsUser != null) {
            return new ApiResponse<>(400, "用户名已存在", null);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encodedPassword);

        userRepository.save(user);

        return ApiResponse.success(Map.of(
                "username", user.getUsername()
        ));
    }

    @PostMapping("/api/login")
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());

        if (user == null) {
            return new ApiResponse<>(401, "用户名或密码错误", null);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return new ApiResponse<>(401, "用户名或密码错误", null);
        }

        String token = JwtUtil.generateToken(user.getUsername());

        return ApiResponse.success(Map.of(
                "token", token
        ));
    }

    @GetMapping("/api/auth/me")
    public ApiResponse<Map<String, String>> me(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return new ApiResponse<>(401, "未登录", null);
        }

        String token = auth.replace("Bearer ", "");

        if (!JwtUtil.isValid(token)) {
            return new ApiResponse<>(401, "token无效", null);
        }

        String username = JwtUtil.parseUsername(token);

        return ApiResponse.success(Map.of(
                "username", username
        ));
    }
}