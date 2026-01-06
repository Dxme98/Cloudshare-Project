package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFolderRequest {
    @NotBlank(message = "Folder name must not be empty")
    @Size(min = 1, max = 50, message = "Name too long")
    String name;
}
