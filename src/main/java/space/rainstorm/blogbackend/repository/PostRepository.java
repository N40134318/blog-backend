package space.rainstorm.blogbackend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import space.rainstorm.blogbackend.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByAuthor(String author, Pageable pageable);

    Page<Post> findByTitleContainingOrContentContaining(String titleKeyword, String contentKeyword, Pageable pageable);

    Page<Post> findByAuthorAndTitleContainingOrAuthorAndContentContaining(
            String author1, String titleKeyword,
            String author2, String contentKeyword,
            Pageable pageable
    );
}