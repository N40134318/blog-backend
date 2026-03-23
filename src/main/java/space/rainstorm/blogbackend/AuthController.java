package space.rainstorm.blogbackend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.dto.LoginRequest;
import space.rainstorm.blogbackend.dto.RefreshTokenRequest;
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
            RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/api/register")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest) {

        String ip = getClientIp(httpServletRequest);
        String registerKey = "register:" + ip;

        if (!rateLimitService.isAllowed(registerKey, REGISTER_LIMIT, REGISTER_WINDOW_MILLIS)) {
            long seconds = rateLimitService.getRemainingSeconds(registerKey, REGISTER_WINDOW_MILLIS);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "注册过于频繁，请 " + seconds + " 秒后再试");
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (password.equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能与用户名相同");
        }

        if (password.matches("^\\d+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为纯数字");
        }

        User existsUser = userRepository.findByUsername(username);
        if (existsUser != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        String encodedPassword = passwordEncoder.encode(password);

        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setRole("user"); // ✅ 默认注册用户角色
        user.setTokenVersion(0);

        userRepository.save(user);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "username", user.getUsername(),
                        "role", user.getRole())));
    }

    @PostMapping("/api/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {

        String username = request.getUsername().trim();
        String ip = getClientIp(httpServletRequest);
        String loginRateKey = "login:" + ip;

        if (!rateLimitService.isAllowed(loginRateKey, LOGIN_LIMIT, LOGIN_WINDOW_MILLIS)) {
            long seconds = rateLimitService.getRemainingSeconds(loginRateKey, LOGIN_WINDOW_MILLIS);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "请求过于频繁，请 " + seconds + " 秒后再试");
        }

        String attemptKey = username + "@" + ip;

        if (loginAttemptService.isLocked(attemptKey)) {
            long seconds = loginAttemptService.getRemainingLockSeconds(attemptKey);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请 " + seconds + " 秒后再试");
        }

        User user = userRepository.findByUsername(username);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFail(attemptKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        loginAttemptService.reset(attemptKey);

        Integer tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String role = user.getRole() == null || user.getRole().isBlank() ? "user" : user.getRole();

        String accessToken = JwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = JwtUtil.generateRefreshToken(user.getUsername(), tokenVersion);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "accessToken", accessToken,
                        "refreshToken", refreshToken,
                        "role", role)));
    }

    @PostMapping("/api/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        String refreshToken = request.getRefreshToken();

        if (!JwtUtil.isRefreshTokenValid(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refreshToken无效或已过期");
        }

        String username = JwtUtil.parseUsername(refreshToken);
        Integer tokenVersion = JwtUtil.parseTokenVersion(refreshToken);

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }

        Integer currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (!currentTokenVersion.equals(tokenVersion)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refreshToken已失效，请重新登录");
        }

        String role = user.getRole() == null || user.getRole().isBlank() ? "user" : user.getRole();

        String newAccessToken = JwtUtil.generateAccessToken(username);
        String newRefreshToken = JwtUtil.generateRefreshToken(username, currentTokenVersion);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "accessToken", newAccessToken,
                        "refreshToken", newRefreshToken,
                        "role", role)));
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<Map<String, String>>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }

        String token = auth.replace("Bearer ", "");

        if (!JwtUtil.isAccessTokenValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token无效或已过期");
        }

        String username = JwtUtil.parseUsername(token);

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }

        String role = user.getRole() == null || user.getRole().isBlank() ? "user" : user.getRole();

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "username", username,
                        "role", role)));
    }

    @PostMapping("/api/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }

        String token = auth.replace("Bearer ", "");

        if (!JwtUtil.isAccessTokenValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token无效或已过期");
        }

        String username = JwtUtil.parseUsername(token);

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }

        Integer currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        user.setTokenVersion(currentTokenVersion + 1);
        userRepository.save(user);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "message", "退出登录成功")));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
