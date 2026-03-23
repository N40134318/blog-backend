package space.rainstorm.blogbackend.dto;

public class AdminCommentItem {

    private Long id;
    private Long postId;
    private String postTitle;
    private String author;
    private String content;
    private Long createdAt;
    private String status;

    public AdminCommentItem() {
    }

    public AdminCommentItem(
            Long id,
            Long postId,
            String postTitle,
            String author,
            String content,
            Long createdAt,
            String status) {
        this.id = id;
        this.postId = postId;
        this.postTitle = postTitle;
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getPostTitle() {
        return postTitle;
    }

    public void setPostTitle(String postTitle) {
        this.postTitle = postTitle;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
