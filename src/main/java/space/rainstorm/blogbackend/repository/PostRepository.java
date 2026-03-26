package space.rainstorm.blogbackend.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import space.rainstorm.blogbackend.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByAuthor(String author, Pageable pageable);

    Page<Post> findByTitleContainingOrContentContaining(
            String titleKeyword,
            String contentKeyword,
            Pageable pageable);

    Page<Post> findByAuthorAndTitleContainingOrAuthorAndContentContaining(
            String author1, String titleKeyword,
            String author2, String contentKeyword,
            Pageable pageable);

    Page<Post> findByStatus(String status, Pageable pageable);

    Page<Post> findByStatusAndTitleContainingOrStatusAndContentContaining(
            String status1, String titleKeyword,
            String status2, String contentKeyword,
            Pageable pageable);

    Page<Post> findByStatusOrderByWeightDescViewCountDescIdDesc(String status, Pageable pageable);

    long countByStatus(String status);

    long count();

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.viewCount = :viewCount WHERE p.id = :id")
    int updateViewCountById(@Param("id") Long id, @Param("viewCount") Long viewCount);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.weight = :weight WHERE p.id = :id")
    int updateWeightById(@Param("id") Long id, @Param("weight") Integer weight);
}
