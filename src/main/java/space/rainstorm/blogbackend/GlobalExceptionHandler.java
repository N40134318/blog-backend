package space.rainstorm.blogbackend;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import space.rainstorm.blogbackend.common.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return new ApiResponse<>(400, "上传文件不能超过 10MB", null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<String> handleException(Exception e) {
        return new ApiResponse<>(500, "服务器内部错误：" + e.getMessage(), null);
    }
}