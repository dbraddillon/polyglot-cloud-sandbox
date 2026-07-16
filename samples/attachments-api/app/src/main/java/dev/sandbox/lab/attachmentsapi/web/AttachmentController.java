package dev.sandbox.lab.attachmentsapi.web;

import dev.sandbox.lab.attachmentsapi.domain.AttachmentContent;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentDetail;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentSummary;
import dev.sandbox.lab.attachmentsapi.service.AttachmentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<AttachmentSummary> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AttachmentDetail> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        // getOriginalFilename()'s own Javadoc documents it can return null. Empirically, this
        // exact stack (embedded Tomcat + Spring's standard servlet-based multipart resolver)
        // normalizes a part with no filename attribute to an empty string instead - confirmed by
        // hand-crafting the raw multipart body, not assumed - so this null branch isn't proven
        // reachable via HTTP here specifically. Guarding it anyway: it's the same one-line
        // defensive pattern the line above already applies to contentType, a real part of the
        // MultipartFile contract, and free of downside - S3AttachmentRepository's
        // Map.of(FILENAME_METADATA_KEY, filename) would NPE on an actual null, not degrade
        // gracefully, if some other multipart implementation ever did produce one.
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
        AttachmentDetail detail = service.upload(filename, contentType, file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @GetMapping("/{key}")
    public ResponseEntity<byte[]> download(@PathVariable String key) {
        AttachmentContent content = service.download(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(content.filename()).build().toString())
                .body(content.content());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        service.delete(key);
        return ResponseEntity.noContent().build();
    }
}
