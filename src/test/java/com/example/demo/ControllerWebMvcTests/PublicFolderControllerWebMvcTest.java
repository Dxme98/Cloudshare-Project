package com.example.demo.ControllerWebMvcTests;

import com.example.demo.config.SecurityConfig;
import com.example.demo.controller.PublicFolderController;
import com.example.demo.dto.response.FileUploadResponse;
import com.example.demo.dto.response.FolderInitResponse;
import com.example.demo.dto.response.FolderResponse;
import com.example.demo.service.PublicShareService;
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
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PublicFolderController.class)
@DisplayName("PublicFolderController (WebMvcTest)")
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class PublicFolderControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicShareService publicShareService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String BASE_URL = "/api/folders";

    @Test
    @DisplayName("POST /api/folders - Sollte 201 Created zurückgeben")
    void initializeFolder_shouldReturn201() throws Exception {
        FolderInitResponse response = new FolderInitResponse();
        response.setFolderId("folder-123");
        response.setOwnerToken("owner-token");
        response.setShareToken("share-token");

        when(publicShareService.initializeFolder()).thenReturn(response);

        mockMvc.perform(post(BASE_URL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folderId").value("folder-123"));
    }

    @Test
    @DisplayName("GET /api/folders/{id} - Sollte Ordner-Metadaten zurückgeben (Public Access)")
    void openFolder_shouldReturn200() throws Exception {
        String folderId = "f-123";
        String token = "valid-token";
        FolderResponse response = new FolderResponse();
        response.setFolderId(folderId);
        response.setFolderName("Test Folder");
        response.setFileMetadataList(new ArrayList<>());

        when(publicShareService.openFolder(token, folderId)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/{folderId}", folderId)
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(folderId));
    }

    @Test
    @DisplayName("POST /api/folders/{id}/files - Sollte Datei-Upload verarbeiten")
    void uploadFile_shouldReturn201() throws Exception {
        String folderId = "f-123";
        String token = "write-token";
        MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                MediaType.TEXT_PLAIN_VALUE, "Hello World".getBytes());

        FileUploadResponse response = FileUploadResponse.create("file-999");

        when(publicShareService.uploadFileWithToken(eq(folderId), eq(token), any())).thenReturn(response);

        mockMvc.perform(multipart(BASE_URL + "/{folderId}/files", folderId)
                        .file(file)
                        .param("token", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value("file-999"));
    }

    @Test
    @DisplayName("DELETE /api/folders/{id}/files/{fid} - Sollte 204 No Content zurückgeben")
    void deleteFile_shouldReturn204() throws Exception {
        String folderId = "f-123";
        String fileId = "file-456";
        String token = "owner-token";

        mockMvc.perform(delete(BASE_URL + "/{folderId}/files/{fileId}", folderId, fileId)
                        .param("token", token))
                .andExpect(status().isNoContent());

        verify(publicShareService).deleteFileWithToken(folderId, fileId, token);
    }

    @Test
    @DisplayName("GET /api/folders/{id}/files/{fid} - Sollte Datei-Stream zurückgeben")
    void downloadFile_shouldReturnFileStream() throws Exception {
        // Given
        String folderId = "f-123";
        String fileId = "file-456";
        String token = "read-token";
        byte[] content = "Echter Inhalt".getBytes();

        S3Resource mockResource = mock(S3Resource.class);
        when(mockResource.getFilename()).thenReturn("test.pdf");
        when(mockResource.contentLength()).thenReturn((long) content.length);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        when(publicShareService.downloadFile(folderId, fileId, token)).thenReturn(mockResource);

        MvcResult result = mockMvc.perform(get(BASE_URL + "/{folderId}/files/{fileId}", folderId, fileId)
                        .param("token", token))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.pdf\""))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, (long) content.length))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(content));
    }
}