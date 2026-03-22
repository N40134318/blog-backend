package space.rainstorm.blogbackend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import space.rainstorm.blogbackend.common.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, message, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<String>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "上传文件不能超过 10MB", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<String>> handleResponseStatusException(ResponseStatusException e) {
        int code = e.getStatusCode().value();
        String message = e.getReason();

        if (message == null || message.isBlank()) {
            if (code == 401) {
                message = "未登录或 token 已失效";
            } else if (code == 403) {
                message = "无权限访问";
            } else if (code == 404) {
                message = "资源不存在";
            } else {
                message = "请求失败";
            }
        }

        return ResponseEntity
                .status(e.getStatusCode())
                .body(new ApiResponse<>(code, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleException(Exception e) {
        System.out.println(">>> GLOBAL EXCEPTION HANDLER TRIGGERED <<<");
        e.printStackTrace();

        String message = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            message += ": " + e.getMessage();
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "服务器内部错误 - " + message, null));
    }
}
