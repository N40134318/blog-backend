package space.rainstorm.blogbackend;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.dto.AdminCommentItem;
import space.rainstorm.blogbackend.dto.CreateCommentRequest;
import space.rainstorm.blogbackend.entity.Comment;
import space.rainstorm.blogbackend.entity.Post;
import space.rainstorm.blogbackend.entity.User;
import space.rainstorm.blogbackend.repository.CommentRepository;
import space.rainstorm.blogbackend.repository.PostRepository;
import space.rainstorm.blogbackend.repository.UserRepository;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class CommentController {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CommentController(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
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

    private User getCurrentUser(String auth) {
        if (isUnauthorized(auth)) {
            return null;
        }
        String username = getCurrentUsername(auth);
        return userRepository.findByUsername(username);
    }

    private boolean isAdmin(String auth) {
        User user = getCurrentUser(auth);
        if (user == null || user.getRole() == null) {
            return false;
        }
        return "admin".equalsIgnoreCase(user.getRole());
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<List<Comment>> list(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!postRepository.existsById(postId)) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        return ApiResponse.success(
                commentRepository.findByPostIdAndStatusOrderByIdDesc(postId, "visible"));
    }

    @GetMapping("/api/admin/comments")
    public ApiResponse<Map<String, Object>> adminComments(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!isAdmin(auth)) {
            return new ApiResponse<>(403, "仅管理员可访问", null);
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Comment> result;

        if (keyword == null || keyword.isBlank()) {
            result = commentRepository.findAllByOrderByIdDesc(pageable);
        } else {
            result = commentRepository.findByAuthorContainingOrContentContainingOrderByIdDesc(
                    keyword,
                    keyword,
                    pageable);
        }

        List<AdminCommentItem> list = result.getContent().stream().map(comment -> {
            String postTitle = "文章不存在";
            Optional<Post> optionalPost = postRepository.findById(comment.getPostId());

            if (optionalPost.isPresent()) {
                Post post = optionalPost.get();
                postTitle = post.getTitle() == null || post.getTitle().isBlank()
                        ? "无标题文章"
                        : post.getTitle();
            }

            return new AdminCommentItem(
                    comment.getId(),
                    comment.getPostId(),
                    postTitle,
                    comment.getAuthor(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getStatus());
        }).collect(Collectors.toList());

        return ApiResponse.success(Map.of(
                "list", list,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<Comment> create(
            @PathVariable Long postId,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody CreateCommentRequest request) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!postRepository.existsById(postId)) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        String username = getCurrentUsername(auth);

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthor(username);
        comment.setContent(request.getContent().trim());
        comment.setCreatedAt(System.currentTimeMillis());
        comment.setStatus("visible");

        return ApiResponse.success(commentRepository.save(comment));
    }

    @PutMapping("/api/admin/comments/{id}/hide")
    public ApiResponse<Comment> hideComment(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!isAdmin(auth)) {
            return new ApiResponse<>(403, "仅管理员可操作", null);
        }

        Optional<Comment> optionalComment = commentRepository.findById(id);
        if (optionalComment.isEmpty()) {
            return new ApiResponse<>(404, "评论不存在", null);
        }

        Comment comment = optionalComment.get();
        comment.setStatus("hidden");

        return ApiResponse.success(commentRepository.save(comment));
    }

    @PutMapping("/api/admin/comments/{id}/restore")
    public ApiResponse<Comment> restoreComment(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!isAdmin(auth)) {
            return new ApiResponse<>(403, "仅管理员可操作", null);
        }

        Optional<Comment> optionalComment = commentRepository.findById(id);
        if (optionalComment.isEmpty()) {
            return new ApiResponse<>(404, "评论不存在", null);
        }

        Comment comment = optionalComment.get();
        comment.setStatus("visible");

        return ApiResponse.success(commentRepository.save(comment));
    }

    @DeleteMapping("/api/comments/{id}")
    public ApiResponse<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        Optional<Comment> optionalComment = commentRepository.findById(id);
        if (optionalComment.isEmpty()) {
            return new ApiResponse<>(404, "评论不存在", null);
        }

        Comment comment = optionalComment.get();
        String username = getCurrentUsername(auth);
        boolean canDelete = username.equals(comment.getAuthor()) || isAdmin(auth);

        if (!canDelete) {
            return new ApiResponse<>(403, "无权限删除这条评论", null);
        }

        commentRepository.deleteById(id);
        return ApiResponse.success("删除成功");
    }
}
