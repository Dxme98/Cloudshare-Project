package com.example.demo.dto.response;

import com.example.demo.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FolderMemberResponse {
    private String userId;
    private String email;
    private Role role;
}