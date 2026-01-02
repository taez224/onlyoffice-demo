package com.example.onlyoffice.sdk;

import com.onlyoffice.model.common.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom User Manager
 * Provides user information for ONLYOFFICE editor
 * <p>
 * Demo implementation: Returns anonymous user
 * <p>
 * TODO: Integrate with authentication system to provide real user data
 */
@Slf4j
@Component
public class CustomUserManager {

    /**
     * Get user information for editor session
     *
     * @param fileId The document's fileKey (UUID) - can be used for user context
     * @return User object with demo user information
     */
    public User getUser(String fileId) {
        log.debug("Creating user for fileId: {}", fileId);

        // Demo: Anonymous user
        // TODO: Replace with actual user from authentication context
        return User.builder()
                .id("demo-user-1")
                .name("Demo User")
                // .image("") // Optional: base64 encoded avatar or URL
                .build();
    }
}
