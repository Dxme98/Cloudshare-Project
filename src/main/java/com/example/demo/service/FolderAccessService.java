package com.example.demo.service;

import com.example.demo.entity.Folder;
import com.example.demo.entity.FolderShare;
import com.example.demo.enums.Role;
import com.example.demo.repository.FolderRepository;
import com.example.demo.repository.FolderShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderAccessService {

    private final FolderRepository folderRepository;
    private final FolderShareRepository shareRepository;

    public Role getUserRole(String userId, String folderId) {
        Folder folder = folderRepository.findById(folderId);
        return getUserRole(userId, folder);
    }

    public Role getUserRole(String userId, Folder folder) {
        if (Objects.equals(folder.getUserId(), userId)) {
            return Role.OWNER;
        }

        return shareRepository.findAccess(userId, folder.getFolderId())
                .map(FolderShare::getRole)
                .orElseThrow(() -> {
                    log.warn("Access denied: User {} to Folder {}", userId, folder.getFolderId());
                    return new AccessDeniedException("Keine Berechtigung für diesen Ordner");
                });
    }

    public void requireRole(String userId, String folderId, Role requiredRole) {
        Role userRole = getUserRole(userId, folderId);
        if (!hasPermission(userRole, requiredRole)) {
            throw new AccessDeniedException("Aktion erfordert " + requiredRole + " Rechte");
        }
    }

    public void requireOwner(String userId, String folderId) {
        Folder folder = folderRepository.findById(folderId);

        if (!Objects.equals(folder.getUserId(), userId)) {
            log.warn("Owner access required: User {} tried to access Folder {}", userId, folderId);
            throw new AccessDeniedException("Nur der Besitzer kann diese Aktion ausführen");
        }
    }

    private boolean hasPermission(Role userRole, Role required) {
        if (required == Role.VIEWER) return true; // Jeder mit irgendeiner Rolle darf lesen
        if (required == Role.CONTRIBUTOR) return userRole != Role.VIEWER; // Owner & Contributor dürfen schreiben
        if (required == Role.OWNER) return userRole == Role.OWNER; // Nur Owner darf Owner-Sachen
        return false;
    }
}