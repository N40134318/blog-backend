package space.rainstorm.blogbackend;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
public class UploadController {

    private static final String UPLOAD_DIR = "/app/uploads";

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

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/png") &&
                 !contentType.equals("image/jpeg") &&
                 !contentType.equals("image/jpg") &&
                 !contentType.equals("image/webp"))) {
            return new ApiResponse<>(400, "只允许上传 png/jpg/jpeg/webp 图片", null);
        }

        long maxSize = 10 * 1024 * 1024L; // 10MB
        if (file.getSize() > maxSize) {
            return new ApiResponse<>(400, "图片不能超过 10MB", null);
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!(suffix.equals(".png") || suffix.equals(".jpg") || suffix.equals(".jpeg") || suffix.equals(".webp"))) {
            return new ApiResponse<>(400, "文件后缀仅支持 png/jpg/jpeg/webp", null);
        }

        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
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