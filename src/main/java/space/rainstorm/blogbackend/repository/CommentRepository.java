package space.rainstorm.blogbackend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import space.rainstorm.blogbackend.entity.Comment;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdAndStatusOrderByIdDesc(Long postId, String status);

    Page<Comment> findAllByOrderByIdDesc(Pageable pageable);

    Page<Comment> findByAuthorContainingOrContentContainingOrderByIdDesc(
            String authorKeyword,
            String contentKeyword,
            Pageable pageable);
}
