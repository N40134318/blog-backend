package space.rainstorm.blogbackend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.dto.LoginRequest;
import space.rainstorm.blogbackend.dto.RegisterRequest;
import space.rainstorm.blogbackend.entity.User;
import space.rainstorm.blogbackend.repository.UserRepository;
import space.rainstorm.blogbackend.security.LoginAttemptService;
import space.rainstorm.blogbackend.security.RateLimitService;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.util.Map;

@RestController
@CrossOrigin(origins = "https://dev.rainstorm.space")
public class AuthController {

    private static final int LOGIN_LIMIT = 10;
    private static final long LOGIN_WINDOW_MILLIS = 60 * 1000L;

    private static final int REGISTER_LIMIT = 3;
    private static final long REGISTER_WINDOW_MILLIS = 10 * 60 * 1000L;

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(
            UserRepository userRepository,
            LoginAttemptService loginAttemptService,
            RateLimitService rateLimitService
    ) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/api/register")
    public ApiResponse<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String ip = getClientIp(httpServletRequest);
        String registerKey = "register:" + ip;

        if (!rateLimitService.isAllowed(registerKey, REGISTER_LIMIT, REGISTER_WINDOW_MILLIS)) {
            long seconds = rateLimitService.getRemainingSeconds(registerKey, REGISTER_WINDOW_MILLIS);
            return new ApiResponse<>(429, "注册过于频繁，请 " + seconds + " 秒后再试", null);
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (password.equalsIgnoreCase(username)) {
            return new ApiResponse<>(400, "密码不能与用户名相同", null);
        }

        if (password.matches("^\\d+$")) {
            return new ApiResponse<>(400, "密码不能为纯数字", null);
        }

        User existsUser = userRepository.findByUsername(username);
        if (existsUser != null) {
            return new ApiResponse<>(400, "用户名已存在", null);
        }

        String encodedPassword = passwordEncoder.encode(password);

        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);

        userRepository.save(user);

        return ApiResponse.success(Map.of(
                "username", user.getUsername()
        ));
    }

    @PostMapping("/api/login")
    public ApiResponse<Map<String, String>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String username = request.getUsername().trim();
        String ip = getClientIp(httpServletRequest);

        String loginRateKey = "login:" + ip;
        if (!rateLimitService.isAllowed(loginRateKey, LOGIN_LIMIT, LOGIN_WINDOW_MILLIS)) {
            long seconds = rateLimitService.getRemainingSeconds(loginRateKey, LOGIN_WINDOW_MILLIS);
            return new ApiResponse<>(429, "请求过于频繁，请 " + seconds + " 秒后再试", null);
        }

        String attemptKey = username + "@" + ip;

        if (loginAttemptService.isLocked(attemptKey)) {
            long seconds = loginAttemptService.getRemainingLockSeconds(attemptKey);
            return new ApiResponse<>(429, "登录失败次数过多，请 " + seconds + " 秒后再试", null);
        }

        User user = userRepository.findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFail(attemptKey);
            return new ApiResponse<>(401, "用户名或密码错误", null);
        }

        loginAttemptService.reset(attemptKey);

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

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}