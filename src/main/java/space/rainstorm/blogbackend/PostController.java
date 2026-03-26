package space.rainstorm.blogbackend;

import jakarta.validation.Valid;
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
import space.rainstorm.blogbackend.dto.UpdatePostStatusRequest;
import space.rainstorm.blogbackend.entity.Post;
import space.rainstorm.blogbackend.entity.User;
import space.rainstorm.blogbackend.repository.PostRepository;
import space.rainstorm.blogbackend.repository.UserRepository;
import space.rainstorm.blogbackend.util.JwtUtil;
import space.rainstorm.blogbackend.util.RedisCacheService;

import java.util.Map;
import java.util.Optional;

@RestController
public class PostController {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RedisCacheService cacheService;

    public PostController(
            PostRepository postRepository,
            UserRepository userRepository,
            RedisCacheService cacheService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.cacheService = cacheService;
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

    private boolean canManagePost(String auth, Post post) {
        if (post == null || isUnauthorized(auth)) {
            return false;
        }
        if (isAdmin(auth)) {
            return true;
        }
        String username = getCurrentUsername(auth);
        return username.equals(post.getAuthor());
    }

    private String normalizeStatus(String status) {
        if ("published".equalsIgnoreCase(status)) {
            return "published";
        }
        return "draft";
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private void clearPublicPostListCache() {
        cacheService.deleteByPrefix("post:list:");
    }

    @GetMapping("/api/posts")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String keyword) {

        String safeKeyword = keyword == null ? "" : keyword.trim();
        String cacheKey = "post:list:" + page + ":" + size + ":" + safeKeyword;

        Object cached = cacheService.get(cacheKey);
        if (cached instanceof Map) {
            return ApiResponse.success((Map<String, Object>) cached);
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> result;

        if (safeKeyword.isBlank()) {
            result = postRepository.findByStatus("published", pageable);
        } else {
            result = postRepository.findByStatusAndTitleContainingOrStatusAndContentContaining(
                    "published", safeKeyword,
                    "published", safeKeyword,
                    pageable);
        }

        Map<String, Object> data = Map.of(
                "list", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages());

        cacheService.set(cacheKey, data, 300);

        return ApiResponse.success(data);
    }

    @GetMapping("/api/posts/my")
    public ApiResponse<Map<String, Object>> myPosts(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String keyword) {
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
                    pageable);
        }

        return ApiResponse.success(Map.of(
                "list", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()));
    }

    @GetMapping("/api/admin/posts")
    public ApiResponse<Map<String, Object>> adminPosts(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String keyword) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!isAdmin(auth)) {
            return new ApiResponse<>(403, "仅管理员可访问", null);
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> result;

        if (keyword == null || keyword.isBlank()) {
            result = postRepository.findAll(pageable);
        } else {
            result = postRepository.findByTitleContainingOrContentContaining(
                    keyword,
                    keyword,
                    pageable);
        }

        return ApiResponse.success(Map.of(
                "list", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()));
    }

    @GetMapping("/api/admin/dashboard")
    public ApiResponse<Map<String, Object>> adminDashboard(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        if (!isAdmin(auth)) {
            return new ApiResponse<>(403, "仅管理员可访问", null);
        }

        PageRequest pageable = PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "id"));
        Page<Post> recentPage = postRepository.findAll(pageable);
        long totalPosts = postRepository.count();
        long draftCount = postRepository.countByStatus("draft");
        long publishedCount = postRepository.countByStatus("published");

        return ApiResponse.success(Map.of(
                "totalPosts", totalPosts,
                "draftCount", draftCount,
                "publishedCount", publishedCount,
                "recentPosts", recentPage.getContent()));
    }

    @GetMapping("/api/posts/{id}")
    public ApiResponse<Post> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        String cacheKey = "post:detail:" + id;

        Object cached = cacheService.get(cacheKey);
        if (cached instanceof Post) {
            return ApiResponse.success((Post) cached);
        }

        Optional<Post> optionalPost = postRepository.findById(id);
        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();

        if ("published".equals(post.getStatus())) {
            cacheService.set(cacheKey, post, 300);
            return ApiResponse.success(post);
        }

        if (!canManagePost(auth, post)) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        return ApiResponse.success(post);
    }

    @PostMapping("/api/posts")
    public ApiResponse<Post> create(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody CreatePostRequest request) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        String username = getCurrentUsername(auth);
        long now = System.currentTimeMillis();

        Post post = new Post();
        post.setTitle(request.getTitle().trim());
        post.setSummary(buildSummary(request.getContent()));
        post.setContent(request.getContent().trim());
        post.setAuthor(username);
        post.setCategory(normalizeText(request.getCategory()));
        post.setTags(normalizeText(request.getTags()));
        post.setCoverImage(normalizeText(request.getCoverImage()));
        post.setStatus(normalizeStatus(request.getStatus()));
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        Post savedPost = postRepository.save(post);

        clearPublicPostListCache();

        return ApiResponse.success(savedPost);
    }

    @PutMapping("/api/posts/{id}")
    public ApiResponse<Post> update(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody CreatePostRequest request) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        Optional<Post> optionalPost = postRepository.findById(id);
        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();
        if (!canManagePost(auth, post)) {
            return new ApiResponse<>(403, "无权限修改这篇文章", null);
        }

        post.setTitle(request.getTitle().trim());
        post.setSummary(buildSummary(request.getContent()));
        post.setContent(request.getContent().trim());
        post.setCategory(normalizeText(request.getCategory()));
        post.setTags(normalizeText(request.getTags()));
        post.setCoverImage(normalizeText(request.getCoverImage()));
        post.setStatus(normalizeStatus(request.getStatus()));
        post.setUpdatedAt(System.currentTimeMillis());

        Post savedPost = postRepository.save(post);

        cacheService.delete("post:detail:" + savedPost.getId());
        clearPublicPostListCache();

        return ApiResponse.success(savedPost);
    }

    @PutMapping("/api/posts/{id}/status")
    public ApiResponse<Post> updateStatus(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody UpdatePostStatusRequest request) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        Optional<Post> optionalPost = postRepository.findById(id);
        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();
        if (!canManagePost(auth, post)) {
            return new ApiResponse<>(403, "无权限修改这篇文章状态", null);
        }

        post.setStatus(normalizeStatus(request.getStatus()));
        post.setUpdatedAt(System.currentTimeMillis());

        Post savedPost = postRepository.save(post);

        cacheService.delete("post:detail:" + savedPost.getId());
        clearPublicPostListCache();

        return ApiResponse.success(savedPost);
    }

    @DeleteMapping("/api/posts/{id}")
    public ApiResponse<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isUnauthorized(auth)) {
            return new ApiResponse<>(401, "未登录", null);
        }

        Optional<Post> optionalPost = postRepository.findById(id);
        if (optionalPost.isEmpty()) {
            return new ApiResponse<>(404, "文章不存在", null);
        }

        Post post = optionalPost.get();
        if (!canManagePost(auth, post)) {
            return new ApiResponse<>(403, "无权限删除这篇文章", null);
        }

        postRepository.deleteById(id);

        cacheService.delete("post:detail:" + id);
        clearPublicPostListCache();

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
