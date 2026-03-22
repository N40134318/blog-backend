package space.rainstorm.blogbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePostStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Size(max = 20, message = "状态长度不能超过 20 个字符")
    private String status;

    public UpdatePostStatusRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}