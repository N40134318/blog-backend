package space.rainstorm.blogbackend;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.dto.CreateCommentRequest;
import space.rainstorm.blogbackend.entity.Comment;
import space.rainstorm.blogbackend.repository.CommentRepository;
import space.rainstorm.blogbackend.repository.PostRepository;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.util.List;
import java.util.Optional;

@RestController
public class CommentController {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentController(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    private boolean isUnauthorized(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return true;
        }

        String token = auth.replace("Bearer ", "");
        return !JwtUtil.isValid(token);
    }

    private String getCurrentUsername(String auth) {
        String token = auth.replace("Bearer ", "");
        return JwtUtil.parseUsername(token);
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<List<Comment>> list(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!postRepository.existsById(postId)) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        return ApiResponse.success(commentRepository.findByPostIdOrderByIdDesc(postId));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<Comment> create(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody CreateCommentRequest request
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!postRepository.existsById(postId)) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        if (request.getContent() == null || request.getContent().isBlank()) {
            return new ApiResponse<>(400, "评论内容不能为空", null);
        }

        String username = getCurrentUsername(auth);

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthor(username);
        comment.setContent(request.getContent());
        comment.setCreatedAt(System.currentTimeMillis());

        return ApiResponse.success(commentRepository.save(comment));
    }

    @DeleteMapping("/api/comments/{id}")
    public ApiResponse<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        Optional<Comment> optionalComment = commentRepository.findById(id);
        if (optionalComment.isEmpty()) {
            return new ApiResponse<>(404, "评论不存在", null);
        }

        String username = getCurrentUsername(auth);
        Comment comment = optionalComment.get();

        if (!username.equals(comment.getAuthor())) {
            return new ApiResponse<>(403, "无权限删除别人的评论", null);
        }

        commentRepository.deleteById(id);
        return ApiResponse.success("删除成功");
    }
}