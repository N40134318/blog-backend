package space.rainstorm.blogbackend;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.rainstorm.blogbackend.common.ApiResponse;
import space.rainstorm.blogbackend.dto.CreatePostRequest;
import space.rainstorm.blogbackend.entity.Post;
import space.rainstorm.blogbackend.repository.PostRepository;
import space.rainstorm.blogbackend.util.JwtUtil;

import java.util.Map;
import java.util.Optional;

@RestController
public class PostController {
    private final PostRepository postRepository;

    public PostController(PostRepository postRepository) {
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

    private String normalizeStatus(String status) {
        if ("published".equalsIgnoreCase(status)) {
            return "published";
        }
        return "draft";
    }

    @GetMapping("/api/posts")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> result;

        if (keyword == null || keyword.isBlank()) {
            result = postRepository.findByStatus("published", pageable);
        } else {
            result = postRepository.findByStatusAndTitleContainingOrStatusAndContentContaining(
                    "published", keyword,
                    "published", keyword,
                    pageable
            );
        }

        return ApiResponse.success(Map.of(
                "list", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    @GetMapping("/api/posts/my")
    public ApiResponse<Map<String, Object>> myPosts(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        String username = getCurrentUsername(auth);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> result;

        if (keyword == null || keyword.isBlank()) {
            result = postRepository.findByAuthor(username, pageable);
        } else {
            result = postRepository.findByAuthorAndTitleContainingOrAuthorAndContentContaining(
                    username, keyword,
                    username, keyword,
                    pageable
            );
        }

        return ApiResponse.success(Map.of(
                "list", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    @GetMapping("/api/posts/{id}")
    public ApiResponse<Post> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        Optional<Post> optionalPost = postRepository.findById(id);
        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();

        if ("published".equals(post.getStatus())) {
            return ApiResponse.success(post);
        }

        if (isUnauthorized(auth)) {
            return new ApiResponse<>(403, "无权限查看该文章", null);
        }

        String username = getCurrentUsername(auth);
        if (!username.equals(post.getAuthor())) {
            return new ApiResponse<>(403, "无权限查看该文章", null);
        }

        return ApiResponse.success(post);
    }

    @PostMapping("/api/posts")
    public ApiResponse<Post> create(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody CreatePostRequest request
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        String username = getCurrentUsername(auth);

        Post post = new Post();
        post.setTitle(request.getTitle());
        post.setSummary(buildSummary(request.getContent()));
        post.setContent(request.getContent());
        post.setAuthor(username);
        post.setCategory(request.getCategory());
        post.setTags(request.getTags());
        post.setCoverImage(request.getCoverImage());
        post.setStatus(normalizeStatus(request.getStatus()));

        return ApiResponse.success(postRepository.save(post));
    }

    @PutMapping("/api/posts/{id}")
    public ApiResponse<Post> update(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody CreatePostRequest request
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        String username = getCurrentUsername(auth);
        Optional<Post> optionalPost = postRepository.findById(id);

        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();
        if (!username.equals(post.getAuthor())) {
            return new ApiResponse<>(403, "无权限修改别人的文章", null);
        }

        post.setTitle(request.getTitle());
        post.setSummary(buildSummary(request.getContent()));
        post.setContent(request.getContent());
        post.setCategory(request.getCategory());
        post.setTags(request.getTags());
        post.setCoverImage(request.getCoverImage());
        post.setStatus(normalizeStatus(request.getStatus()));

        return ApiResponse.success(postRepository.save(post));
    }

    @DeleteMapping("/api/posts/{id}")
    public ApiResponse<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        String username = getCurrentUsername(auth);
        Optional<Post> optionalPost = postRepository.findById(id);

        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();
        if (!username.equals(post.getAuthor())) {
            return new ApiResponse<>(403, "无权限删除别人的文章", null);
        }

        postRepository.deleteById(id);
        return ApiResponse.success("删除成功");
    }

    private String buildSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String plain = content
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!\\[[^\\]]*\\]\\([^\\)]*\\)", " ")
                .replaceAll("\\[[^\\]]*\\]\\([^\\)]*\\)", " ")
                .replaceAll("#{1,6}\\s*", "")
                .replaceAll("[>*_~\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return plain.length() > 120 ? plain.substring(0, 120) + "..." : plain;
    }
}