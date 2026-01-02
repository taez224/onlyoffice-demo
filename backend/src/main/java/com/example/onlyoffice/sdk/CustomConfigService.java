package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.manager.url.UrlManager;
import com.onlyoffice.model.common.User;
import com.onlyoffice.model.documenteditor.config.document.Permissions;
import com.onlyoffice.service.documenteditor.config.DefaultConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom Config Service
 * Extends DefaultConfigService to provide Permission and User configuration
 * <p>
 * Overrides SDK's extension points:
 * - getPermissions(fileId): Returns document permissions (edit, download, print, etc.)
 * - getUser(): Returns user information (id, name, avatar)
 * <p>
 * The SDK automatically calls these methods when creating Config via createConfig()
 */
@Slf4j
@Component
public class CustomConfigService extends DefaultConfigService {

    private final CustomPermissionManager permissionManager;
    private final CustomUserManager userManager;

    public CustomConfigService(DocumentManager documentManager,
                               UrlManager urlManager,
                               JwtManager jwtManager,
                               SettingsManager settingsManager,
                               CustomPermissionManager permissionManager,
                               CustomUserManager userManager) {
        super(documentManager, urlManager, jwtManager, settingsManager);
        this.permissionManager = permissionManager;
        this.userManager = userManager;
    }

    /**
     * SDK Extension Point: Get document permissions
     * Called by SDK's getDocument() during config creation
     */
    @Override
    public Permissions getPermissions(String fileId) {
        log.debug("Getting permissions for fileId: {}", fileId);
        return permissionManager.getPermission(fileId);
    }

    /**
     * SDK Extension Point: Get user information
     * Called by SDK's getEditorConfig() during config creation
     */
    @Override
    public User getUser() {
        log.debug("Getting user information");
        return userManager.getUser(null); // fileId not needed for demo user
    }
}
