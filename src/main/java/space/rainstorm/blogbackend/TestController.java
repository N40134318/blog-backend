package space.rainstorm.blogbackend;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import space.rainstorm.blogbackend.common.ApiResponse;

import java.util.Map;

@RestController
@CrossOrigin(origins = "https://dev.rainstorm.space")
public class TestController {

    @GetMapping("/api/test")
    public ApiResponse<Map<String, String>> test() {
        return ApiResponse.success(Map.of("message", "hello from backend"));
    }
}