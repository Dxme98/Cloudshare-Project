package com.example.demo.ControllerWebMvcTests;

import com.example.demo.config.SecurityConfig;
import com.example.demo.controller.DashboardController;
import com.example.demo.dto.request.CreateFolderRequest;
import com.example.demo.dto.request.ShareRequest;
import com.example.demo.dto.response.*;
import com.example.demo.enums.Role;
import com.example.demo.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.s3.S3Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("DashboardController (WebMvcTest)")
class DashboardControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String BASE_URL = "/api/dashboard/folders";
    private static final String MOCK_USER_ID = "auth0|123456";

    // --- Bestehende Tests (unverändert) ---

    @Test
    @DisplayName("GET /api/dashboard/folders - Sollte Liste der eigenen Ordner zurückgeben")
    void getMyFolders_shouldReturnList() throws Exception {
        FolderSummaryResponse folder = FolderSummaryResponse.builder()
                .id("f-1")
                .name("Mein Ordner")
                .build();
        when(dashboardService.getMyFolders(MOCK_USER_ID)).thenReturn(List.of(folder));

        mockMvc.perform(get(BASE_URL)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("f-1"))
                .andExpect(jsonPath("$[0].name").value("Mein Ordner"));
    }

    @Test
    @DisplayName("POST /api/dashboard/folders - Sollte neuen permanenten Ordner erstellen")
    void createPermanentFolder_shouldReturn201() throws Exception {
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("Neuer Ordner");

        FolderInitResponse response = new FolderInitResponse();
        response.setFolderId("new-f-1");

        when(dashboardService.createPermanentFolder(eq(MOCK_USER_ID), any(CreateFolderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value("new-f-1"));
    }

    @Test
    @DisplayName("GET /api/dashboard/folders/{id} - Sollte Ordner-Details laden")
    void openFolder_shouldReturnDetails() throws Exception {
        String folderId = "f-1";
        FolderResponse response = new FolderResponse();
        response.setFolderId(folderId);

        when(dashboardService.openFolder(folderId, MOCK_USER_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/{folderId}", folderId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(folderId));
    }

    @Test
    @DisplayName("POST /api/dashboard/folders/{id}/files - Sollte Datei hochladen (Auth)")
    void uploadFile_shouldUploadSuccessfully() throws Exception {
        String folderId = "f-1";
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, "content".getBytes());
        FileUploadResponse response = FileUploadResponse.create("file-99");

        when(dashboardService.uploadFile(eq(folderId), eq(MOCK_USER_ID), any())).thenReturn(response);

        mockMvc.perform(multipart(BASE_URL + "/{folderId}/files", folderId)
                        .file(file)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value("file-99"));
    }

    @Test
    @DisplayName("DELETE /api/dashboard/folders/{id}/members/{uid} - Sollte Collaborator entfernen")
    void removeCollaborator_shouldReturn204() throws Exception {
        String folderId = "f-1";
        String targetUserToRemove = "other-user";

        mockMvc.perform(delete(BASE_URL + "/{folderId}/members/{targetUserId}", folderId, targetUserToRemove)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isNoContent());

        verify(dashboardService).removeCollaborator(folderId, MOCK_USER_ID, targetUserToRemove);
    }

    @Test
    @DisplayName("GET /api/dashboard/folders/{id}/files/{fid} - Sollte Download Stream starten")
    void downloadFile_shouldStreamContent() throws Exception {
        String folderId = "f-1";
        String fileId = "file-1";
        byte[] content = "Secret Content".getBytes();

        S3Resource mockResource = mock(S3Resource.class);
        when(mockResource.getFilename()).thenReturn("secret.txt");
        when(mockResource.contentLength()).thenReturn((long) content.length);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        when(dashboardService.downloadFile(folderId, fileId, MOCK_USER_ID)).thenReturn(mockResource);

        MvcResult mvcResult = mockMvc.perform(get(BASE_URL + "/{folderId}/files/{fileId}", folderId, fileId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"secret.txt\""))
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("GET /api/dashboard/folders - Sollte 401 Unauthorized sein ohne Token")
    void getMyFolders_shouldFailWithoutToken() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // --- NEUE TESTS AB HIER ---

    @Test
    @DisplayName("GET /api/dashboard/folders/{id}/members - Sollte Mitglieder anzeigen")
    void getFolderMembers_shouldReturnList() throws Exception {
        String folderId = "f-1";
        FolderMemberResponse member = FolderMemberResponse.builder()
                .email("user@test.com")
                .role(Role.CONTRIBUTOR)
                .userId("user-2")
                .build();

        when(dashboardService.getFolderMembers(folderId, MOCK_USER_ID)).thenReturn(List.of(member));

        mockMvc.perform(get(BASE_URL + "/{folderId}/members", folderId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-2"))
                .andExpect(jsonPath("$[0].email").value("user@test.com"));
    }

    @Test
    @DisplayName("PUT /api/dashboard/folders/{id}/share-token - Sollte Token erneuern")
    void updateShareToken_shouldReturnNewToken() throws Exception {
        String folderId = "f-1";
        ShareTokenResponse tokenResponse = ShareTokenResponse.create("new-share-token");

        when(dashboardService.updateShareToken(folderId, MOCK_USER_ID)).thenReturn(tokenResponse);

        mockMvc.perform(put(BASE_URL + "/{folderId}/share-token", folderId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-share-token"));
    }

    @Test
    @DisplayName("DELETE /api/dashboard/folders/{id}/files/{fid} - Sollte Datei löschen (204)")
    void deleteFile_shouldReturn204() throws Exception {
        String folderId = "f-1";
        String fileId = "file-99";

        mockMvc.perform(delete(BASE_URL + "/{folderId}/files/{fileId}", folderId, fileId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isNoContent());

        verify(dashboardService).deleteFile(folderId, fileId, MOCK_USER_ID);
    }

    @Test
    @DisplayName("DELETE /api/dashboard/folders/{id} - Sollte Ordner löschen (204)")
    void deleteFolder_shouldReturn204() throws Exception {
        String folderId = "f-1";

        mockMvc.perform(delete(BASE_URL + "/{folderId}", folderId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isNoContent());

        verify(dashboardService).deleteFolder(folderId, MOCK_USER_ID);
    }

    @Test
    @DisplayName("POST /api/dashboard/folders/{id}/share - Sollte Ordner teilen (204)")
    void shareFolder_shouldReturn204() throws Exception {
        String folderId = "f-1";
        ShareRequest shareRequest = new ShareRequest();
        shareRequest.setRole(Role.VIEWER);
        shareRequest.setTargetEmail("friend@example.com");

        mockMvc.perform(post(BASE_URL + "/{folderId}/share", folderId)
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isNoContent());

        verify(dashboardService).shareFolder(eq(folderId), eq(MOCK_USER_ID), any(ShareRequest.class));
    }

    @Test
    @DisplayName("GET /api/dashboard/folders/shared - Sollte Liste geteilter Ordner liefern")
    void getSharedFolders_shouldReturnList() throws Exception {
        SharedFolderResponse sharedFolder = new SharedFolderResponse();
        sharedFolder.setFolderId("shared-f-1");
        sharedFolder.setFolderName("Shared with me");

        when(dashboardService.getSharedFolders(MOCK_USER_ID)).thenReturn(List.of(sharedFolder));

        mockMvc.perform(get(BASE_URL + "/shared")
                        .with(jwt().jwt(builder -> builder.claim("sub", MOCK_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].folderId").value("shared-f-1"))
                .andExpect(jsonPath("$[0].folderName").value("Shared with me"));
    }
}