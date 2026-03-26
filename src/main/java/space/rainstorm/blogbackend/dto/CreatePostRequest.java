package space.rainstorm.blogbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreatePostRequest {
    @NotBlank(message = "文章标题不能为空")
    @Size(min = 1, max = 100, message = "文章标题长度需在 1 到 100 个字符之间")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    @Size(min = 1, max = 50000, message = "文章内容长度需在 1 到 50000 个字符之间")
    private String content;

    @Size(max = 30, message = "分类长度不能超过 30 个字符")
    private String category;

    @Size(max = 100, message = "标签总长度不能超过 100 个字符")
    private String tags;

    @Size(max = 255, message = "封面地址长度不能超过 255 个字符")
    private String coverImage;

    @Size(max = 20, message = "状态值长度不能超过 20 个字符")
    private String status;

    private Integer weight;

    public CreatePostRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }
}
