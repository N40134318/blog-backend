package space.rainstorm.blogbackend;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class UploadController {

    private static final String UPLOAD_DIR = "/app/uploads";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            ".png",
            ".jpg",
            ".jpeg",
            ".webp"
    );

    private boolean isUnauthorized(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return true;
        }
        String token = auth.replace("Bearer ", "");
        return !JwtUtil.isValid(token);
    }

    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (file == null || file.isEmpty()) {
            return new ApiResponse<>(400, "文件不能为空", null);
        }

        long maxSize = 10 * 1024 * 1024L;
        if (file.getSize() > maxSize) {
            return new ApiResponse<>(400, "图片不能超过 10MB", null);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return new ApiResponse<>(400, "只允许上传 png/jpg/jpeg/webp 图片", null);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return new ApiResponse<>(400, "文件名无效", null);
        }

        String safeFilename = originalFilename.trim().replace("\\", "/");
        String suffix = "";

        int lastDotIndex = safeFilename.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            suffix = safeFilename.substring(lastDotIndex).toLowerCase();
        }

        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            return new ApiResponse<>(400, "文件后缀仅支持 png/jpg/jpeg/webp", null);
        }

        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            return new ApiResponse<>(500, "上传目录创建失败", null);
        }

        String filename = UUID.randomUUID() + suffix;
        File dest = new File(uploadDir, filename);

        file.transferTo(dest);

        String url = "https://dev.rainstorm.space/uploads/" + filename;

        return ApiResponse.success(Map.of(
                "url", url
        ));
    }
}