package space.rainstorm.blogbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import space.rainstorm.blogbackend.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}