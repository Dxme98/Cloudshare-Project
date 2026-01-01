package com.example.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {
    private final FileStorageService fileStorageService;

    /**
     * Listet alle Ordner des eingeloggten Users.
     * Die User-ID kommt sicher aus dem Token (kann nicht gefälscht werden).
     */
    @GetMapping("/folders")
    public ResponseEntity<List<Folder>> getMyFolders(@AuthenticationPrincipal Jwt jwt) {
        // "sub" ist der Standard-Claim für die User ID in Cognito (UUID)
        String userId = jwt.getClaimAsString("sub");

        List<Folder> folders = fileStorageService.getFoldersByUser(userId);
        return ResponseEntity.ok(folders);
    }

    /**
     * Erstellt einen neuen PERMANENTEN Ordner
     */
    @PostMapping("/folders")
    public ResponseEntity<FolderInitResponse> createPermanentFolder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "My Project") String name) {

        String userId = jwt.getClaimAsString("sub");

        FolderInitResponse response = fileStorageService.createPermanentFolder(userId, name);
        return ResponseEntity.ok(response);
    }
}
