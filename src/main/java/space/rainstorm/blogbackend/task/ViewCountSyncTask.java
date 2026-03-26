package space.rainstorm.blogbackend.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.rainstorm.blogbackend.repository.PostRepository;
import space.rainstorm.blogbackend.util.RedisCacheService;

import java.util.Set;

@Component
public class ViewCountSyncTask {

    private final RedisCacheService cacheService;
    private final PostRepository postRepository;

    public ViewCountSyncTask(
            RedisCacheService cacheService,
            PostRepository postRepository) {
        this.cacheService = cacheService;
        this.postRepository = postRepository;
    }

    @Scheduled(fixedDelayString = "${blog.view-count.sync-delay-ms:60000}")
    public void syncViewCountToDatabase() {
        System.out.println("[ViewCountSyncTask] sync start");

        Set<String> keys = cacheService.keys("post:view:*");
        if (keys == null || keys.isEmpty()) {
            System.out.println("[ViewCountSyncTask] no redis view keys");
            return;
        }

        System.out.println("[ViewCountSyncTask] found keys: " + keys);

        boolean updated = false;

        for (String key : keys) {
            try {
                String value = cacheService.getString(key);
                System.out.println("[ViewCountSyncTask] key=" + key + ", value=" + value);

                if (value == null || value.isBlank()) {
                    continue;
                }

                String postIdText = key.substring("post:view:".length());
                Long postId = Long.parseLong(postIdText);
                Long viewCount = Long.parseLong(value);

                int rows = postRepository.updateViewCountById(postId, viewCount);
                System.out.println(
                        "[ViewCountSyncTask] update postId=" + postId + ", viewCount=" + viewCount + ", rows=" + rows);

                if (rows > 0) {
                    updated = true;
                }
            } catch (Exception e) {
                System.out.println("[ViewCountSyncTask] sync error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (updated) {
            cacheService.deleteByPrefix("post:list:");
            System.out.println("[ViewCountSyncTask] post:list cache cleared");
        }

        System.out.println("[ViewCountSyncTask] sync end");
    }
}
