package com._thproject._thproject_web.oracle.controller;

import com._thproject._thproject_web.oracle.service.DicomImageService;
import com._thproject._thproject_web.oracle.service.ImageStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Tag(name = "DICOM Image Download API", description = "DICOM 이미지 파일 다운로드 및 보기 API")
@RestController
@ConditionalOnProperty(name = "spring.datasource.oracle.enabled", havingValue = "true")
@RequestMapping("/api/images")
public class ImageDownloadController {

    private final ImageStorageService imageStorageService;
    private final DicomImageService dicomImageService;

    public ImageDownloadController(ImageStorageService imageStorageService, 
                                 DicomImageService dicomImageService) {
        this.imageStorageService = imageStorageService;
        this.dicomImageService = dicomImageService;
    }

    // downloadImageByEncodedPath 메서드도 동일하게 CORS 관련 코드가 없어야 합니다.
    @GetMapping("/encoded-download")
    public ResponseEntity<Resource> downloadImageByEncodedPath(
            @RequestParam String encodedPath) {
        
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedPath);
            String fullPath = new String(decodedBytes, StandardCharsets.UTF_8);

            Path pathObject = Paths.get(fullPath);
            String directoryPath = pathObject.getParent().toString();
            String fileName = pathObject.getFileName().toString();

            Resource resource = imageStorageService.loadImageAsResource(directoryPath, fileName);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/dicom");

            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception e) {
            System.err.println("파일 다운로드 실패: encodedPath=" + encodedPath);
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/encoded-view")
    public ResponseEntity<Resource> viewImageByEncodedPath(
            @RequestParam String encodedPath) {
        
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedPath);
            String fullPath = new String(decodedBytes, StandardCharsets.UTF_8);

            Path pathObject = Paths.get(fullPath);
            String directoryPath = pathObject.getParent().toString();
            String fileName = pathObject.getFileName().toString();

            Resource resource = imageStorageService.loadImageAsResource(directoryPath, fileName);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/dicom");

            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Convert DICOM to image format (JPG/PNG)", 
              description = "Converts a DICOM file to JPG or PNG format")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully converted DICOM to image", 
                    content = @Content(mediaType = "image/jpeg, image/png")),
        @ApiResponse(responseCode = "404", description = "DICOM file not found"),
        @ApiResponse(responseCode = "400", description = "Invalid format or conversion error")
    })
    @GetMapping("/convert-dicom")
    public ResponseEntity<Resource> convertDicomToImage(
            @Parameter(description = "Base64 encoded path to the DICOM file") 
            @RequestParam String encodedPath,
            @Parameter(description = "Output image format (jpg or png)", example = "jpg") 
            @RequestParam(defaultValue = "jpg") String format) {
        
        try {
            // Decode the path
            byte[] decodedBytes = Base64.getDecoder().decode(encodedPath);
            String fullPath = new String(decodedBytes, StandardCharsets.UTF_8);

            // Get file path components
            Path pathObject = Paths.get(fullPath);
            String directoryPath = pathObject.getParent().toString();
            String fileName = pathObject.getFileName().toString();

            // Load the DICOM file as a resource
            Resource dicomResource = imageStorageService.loadImageAsResource(directoryPath, fileName);
            
            // Convert DICOM to image
            try (InputStream dicomStream = dicomResource.getInputStream()) {
                byte[] imageBytes = dicomImageService.convertDicomToImage(dicomStream, format);
                
                // Set appropriate content type
                String contentType = format.equalsIgnoreCase("png") ? "image/png" : "image/jpeg";
                
                // Create response
                ByteArrayResource resource = new ByteArrayResource(imageBytes);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(contentType));
                headers.setContentLength(imageBytes.length);
                headers.setContentDispositionFormData("inline", fileName + "." + format.toLowerCase());
                
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(resource);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}