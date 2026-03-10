package fit.tdc.QuanLyTaiLieu.Controller;

import fit.tdc.QuanLyTaiLieu.Model.Document;
import fit.tdc.QuanLyTaiLieu.Model.User;
import fit.tdc.QuanLyTaiLieu.repository.DocumentRepository;
import fit.tdc.QuanLyTaiLieu.repository.UserRepository;
import jakarta.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;


@Controller
@RequestMapping("/documents")
public class DocumentController {

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private UserRepository userRepository;

	@Value("${upload.dir}")
	private String uploadDir;

	@GetMapping
	public String getAllDocuments(HttpSession session, Model model,
	        @RequestParam(required = false) String search,
	        @RequestParam(required = false, name = "filterType") String fileType,
	        @RequestParam(defaultValue = "0") int page) {
	    if (!isLoggedIn(session))
	        return "redirect:/login";

	    Integer userId = (Integer) session.getAttribute("userId");
	    Pageable pageable = PageRequest.of(page, 10);
	    Page<Document> documents;

	    if (search != null && !search.isEmpty() && fileType != null && !fileType.isEmpty()) {
	        documents = documentRepository.findByFilenameContainingAndFileType(search, fileType, pageable);
	    } else if (search != null && !search.isEmpty()) {
	        documents = documentRepository.findByFilenameContaining(search, pageable);
	    } else if (fileType != null && !fileType.isEmpty()) {
	        documents = documentRepository.findByFileType(fileType, pageable);
	    } else {
	        documents = documentRepository.findByUserIdOrStatus(userId.longValue(), 2, pageable);
	    }

	    User currentUser = (User) session.getAttribute("loggedInUser");

	    model.addAttribute("currentUser", currentUser);
	    model.addAttribute("documents", documents.getContent());
	    model.addAttribute("currentPage", page + 1); // Trang hiện tại (từ 1)
	    model.addAttribute("totalPages", documents.getTotalPages()); // Tổng số trang
	    model.addAttribute("hasNext", documents.hasNext());
	    model.addAttribute("search", search);
	    model.addAttribute("filterType", fileType);
	    model.addAttribute("username", currentUser.getUsername());

	    return "home";
	}
	@GetMapping("/upload")
	public String showUploadForm(HttpSession session) {
	    return isLoggedIn(session) ? "upload" : "redirect:/login";
	}

	@PostMapping("/upload")
	public String uploadDocuments(@RequestParam("files") MultipartFile[] files,
	                              @RequestParam(value = "status", defaultValue = "false") boolean status,
	                              HttpSession session,
	                              RedirectAttributes redirectAttributes) {

	    // Kiểm tra đăng nhập
	    if (!isLoggedIn(session)) {
	        return "redirect:/login";
	    }

	    // Kiểm tra file upload
	    if (files == null || files.length == 0) {
	        redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ít nhất một file để upload!");
	        return "redirect:/documents/upload";
	    }

	    // Kiểm tra tổng dung lượng
	    long totalSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
	    if (totalSize > 10 * 1024 * 1024) { 
	        redirectAttributes.addFlashAttribute("error", "Tổng dung lượng không được vượt quá 10MB!");
	        return "redirect:/documents/upload";
	    }

	    try {
	        Integer userId = (Integer) session.getAttribute("userId");
	        if (userId == null) {
	            redirectAttributes.addFlashAttribute("error", "Bạn chưa đăng nhập!");
	            return "redirect:/login";
	        }

	        Optional<User> userOpt = userRepository.findById(userId);
	        if (!userOpt.isPresent()) {
	            redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng!");
	            return "redirect:/documents/upload";
	        }

	        User user = userOpt.get();
	        int successCount = 0;

	        for (MultipartFile file : files) {
	            String originalFilename = file.getOriginalFilename();
	            if (originalFilename == null || originalFilename.trim().isEmpty()) continue;

	            // Tách tên file và đuôi
	            String filenameNoExt = originalFilename.contains(".")
	                    ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
	                    : originalFilename;

	            String extension = originalFilename.contains(".")
	                    ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1)
	                    : "";

	            // Kiểm tra trùng tên 
	            if (documentRepository.existsByFilenameAndUserId(filenameNoExt, user.getId())) {
	                // Lưu file ngay vào thư mục tạm để tránh mất file
	                Path tempDir = Paths.get(uploadDir, "temp");
	                if (!Files.exists(tempDir)) {
	                    Files.createDirectories(tempDir);
	                }
	                
	                String tempFileName = "temp_" + System.currentTimeMillis() + "_" + originalFilename;
	                Path tempFilePath = tempDir.resolve(tempFileName);
	                
	                try (InputStream is = file.getInputStream()) {
	                    Files.copy(is, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
	                }
	                
	                // Lưu thông tin vào session
	                session.setAttribute("tempFilePath", tempFilePath.toString());
	                session.setAttribute("originalFilename", originalFilename);
	                session.setAttribute("fileExtension", extension);
	                session.setAttribute("fileStatus", status);
	                session.setAttribute("fileSize", file.getSize());
	                session.setAttribute("fileUserId", user.getId());
	                session.setAttribute("fileUploadTime", LocalDateTime.now());
	                
	                redirectAttributes.addFlashAttribute("fileName", filenameNoExt);
	                return "redirect:/documents/confirm-upload";
	            }

	            // Upload file bình thường
	            if (uploadSingleFile(file, user, status)) {
	                successCount++;
	            }
	        }

	        redirectAttributes.addFlashAttribute("success", "Upload thành công " + successCount + " tài liệu!");
	        return "redirect:/documents";

	    } catch (Exception e) {
	        e.printStackTrace();
	        redirectAttributes.addFlashAttribute("error", "Lỗi khi upload: " + e.getMessage());
	        return "redirect:/documents/upload";
	    }
	}

	@GetMapping("/confirm-upload")
	public String showConfirmUpload(HttpSession session, Model model) {
	    String fileName = (String) session.getAttribute("originalFilename");
	    if (fileName == null) {
	        return "redirect:/documents/upload";
	    }
	    
	    // Tách tên file để hiển thị
	    String filenameNoExt = fileName.contains(".")
	            ? fileName.substring(0, fileName.lastIndexOf("."))
	            : fileName;
	    
	    model.addAttribute("fileName", filenameNoExt);
	    model.addAttribute("originalFilename", fileName);
	    return "confirm-upload";
	}

	@PostMapping("/confirm-upload")
	public String handleConfirmUpload(@RequestParam("action") String action,
	                                  @RequestParam(value = "newFilename", required = false) String newName,
	                                  HttpSession session,
	                                  RedirectAttributes redirectAttributes) {
	    
	    String tempFilePathStr = (String) session.getAttribute("tempFilePath");
	    
	    if ("cancel".equals(action)) {
	        // Xóa file tạm và clear session
	        cleanupTempFileAndSession(tempFilePathStr, session);
	        redirectAttributes.addFlashAttribute("message", "Upload đã bị hủy.");
	        return "redirect:/documents/upload";
	    }

	    if ("upload".equals(action)) {
	        if (newName == null || newName.trim().isEmpty()) {
	            redirectAttributes.addFlashAttribute("error", "Vui lòng nhập tên file mới!");
	            return "redirect:/documents/confirm-upload";
	        }

	        try {
	            String extension = (String) session.getAttribute("fileExtension");
	            Long size = (Long) session.getAttribute("fileSize");
	            Integer userId = (Integer) session.getAttribute("fileUserId");
	            Boolean status = (Boolean) session.getAttribute("fileStatus");
	            LocalDateTime uploadTime = (LocalDateTime) session.getAttribute("fileUploadTime");

	            Optional<User> userOpt = userRepository.findById(userId);
	            if (!userOpt.isPresent()) {
	                cleanupTempFileAndSession(tempFilePathStr, session);
	                redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng!");
	                return "redirect:/documents/upload";
	            }

	            // Kiểm tra tên mới có trùng không
	            if (documentRepository.existsByFilenameAndUserId(newName.trim(), userId)) {
	                redirectAttributes.addFlashAttribute("error", "Tên file '" + newName + "' cũng đã tồn tại!");
	                return "redirect:/documents/confirm-upload";
	            }

	            User user = userOpt.get();
	            String fullFilename = newName.trim() + "." + extension;

	            // Tạo thư mục upload chính
	            Path uploadPath = Paths.get(uploadDir);
	            if (!Files.exists(uploadPath)) {
	                Files.createDirectories(uploadPath);
	            }

	            // Copy file từ temp sang thư mục chính
	            Path finalFilePath = uploadPath.resolve(fullFilename);
	            Path tempFilePath = Paths.get(tempFilePathStr);
	            
	            Files.copy(tempFilePath, finalFilePath, StandardCopyOption.REPLACE_EXISTING);

	            // Tạo Document
	            Document doc = new Document();
	            doc.setFilename(newName.trim());
	            doc.setFileType(extension);
	            doc.setFilePath(finalFilePath.toString());
	            doc.setSize(size);
	            doc.setTimeUpload(uploadTime);
	            doc.setUser(user);
	            doc.setStatus(status ? 1 : 0);

	            documentRepository.save(doc);

	            // Cleanup
	            cleanupTempFileAndSession(tempFilePathStr, session);

	            redirectAttributes.addFlashAttribute("success", "Upload thành công với tên mới: " + newName);
	            return "redirect:/documents";

	        } catch (Exception e) {
	            e.printStackTrace();
	            cleanupTempFileAndSession(tempFilePathStr, session);
	            redirectAttributes.addFlashAttribute("error", "Lỗi khi upload: " + e.getMessage());
	            return "redirect:/documents/confirm-upload";
	        }
	    }

	    redirectAttributes.addFlashAttribute("error", "Hành động không hợp lệ.");
	    return "redirect:/documents/confirm-upload";
	}

	// Helper method để upload file đơn
	private boolean uploadSingleFile(MultipartFile file, User user, boolean status) {
	    try {
	        String originalFilename = file.getOriginalFilename();
	        if (originalFilename == null) return false;

	        String filenameNoExt = originalFilename.contains(".")
	                ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
	                : originalFilename;

	        String extension = originalFilename.contains(".")
	                ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1)
	                : "";

	        // Tạo thư mục upload nếu chưa có
	        Path uploadPath = Paths.get(uploadDir);
	        if (!Files.exists(uploadPath)) {
	            Files.createDirectories(uploadPath);
	        }
	        
	        // Lưu file vật lý
	        Path filePath = uploadPath.resolve(originalFilename);
	        try (InputStream is = file.getInputStream()) {
	            Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
	        }

	        // Tạo Document
	        Document doc = new Document();
	        doc.setFilename(filenameNoExt);
	        doc.setFileType(extension);
	        doc.setFilePath(filePath.toString());
	        doc.setSize(file.getSize());
	        doc.setTimeUpload(LocalDateTime.now());
	        doc.setUser(user);
	        doc.setStatus(status ? 1 : 0);

	        documentRepository.save(doc);
	        return true;
	        
	    } catch (Exception e) {
	        e.printStackTrace();
	        return false;
	    }
	}

	// Helper method để cleanup temp file và session
	private void cleanupTempFileAndSession(String tempFilePathStr, HttpSession session) {
	    // Xóa file tạm
	    if (tempFilePathStr != null) {
	        try {
	            Path tempFilePath = Paths.get(tempFilePathStr);
	            Files.deleteIfExists(tempFilePath);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	    
	    // Clear session
	    session.removeAttribute("tempFilePath");
	    session.removeAttribute("originalFilename");
	    session.removeAttribute("fileExtension");
	    session.removeAttribute("fileStatus");
	    session.removeAttribute("fileSize");
	    session.removeAttribute("fileUserId");
	    session.removeAttribute("fileUploadTime");
	}

	
	@GetMapping("/download/{id}")
	public ResponseEntity<Resource> downloadDocument(@PathVariable Integer id, HttpSession session) {
		if (!isLoggedIn(session)) return ResponseEntity.notFound().build();

		Optional<Document> docOpt = documentRepository.findById(id);
		if (!docOpt.isPresent()) return ResponseEntity.notFound().build();

		Document doc = docOpt.get();
		Integer userId = (Integer) session.getAttribute("userId");

		if (doc.getStatus() == 0 && !doc.getUser().getId().equals(userId)) return ResponseEntity.notFound().build();

		try {
		    Path path = Paths.get(doc.getFilePath());
		    Resource resource = new UrlResource(path.toUri());

		    if (resource.exists() && resource.isReadable()) {
		        // Xác định loại MIME (Content-Type) dựa trên đuôi file
		        String contentType = Files.probeContentType(path);
		        if (contentType == null) {
		            contentType = "application/octet-stream"; // fallback nếu không xác định được
		        }

		        return ResponseEntity.ok()
		                .contentType(MediaType.parseMediaType(contentType))
		                .header(HttpHeaders.CONTENT_DISPOSITION,
		                        "attachment; filename=\"" + doc.getFilename() + "." + doc.getFileType() + "\"")
		                .body(resource);
		    }
		} catch (Exception e) {
		    e.printStackTrace();
		}
		return ResponseEntity.notFound().build();
	}

	@PostMapping("/delete/{id}")
	public String deleteDocument(@PathVariable Integer id, HttpSession session, RedirectAttributes redirectAttributes) {
		if (!isLoggedIn(session)) return "redirect:/login";

		Optional<Document> docOpt = documentRepository.findById(id);
		if (!docOpt.isPresent()) {
			redirectAttributes.addFlashAttribute("error", "Không tìm thấy tài liệu!");
			return "redirect:/documents";
		}

		Document doc = docOpt.get();
		Integer userId = (Integer) session.getAttribute("userId");

		if (!doc.getUser().getId().equals(userId)) {
			redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xóa tài liệu này!");
			return "redirect:/documents";
		}

		try {
			Files.deleteIfExists(Paths.get(doc.getFilePath()));
			documentRepository.delete(doc);
			redirectAttributes.addFlashAttribute("success", "Xóa tài liệu thành công!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa tài liệu!");
		}

		return "redirect:/documents";
	}

	@GetMapping("/preview/{id}")
	public ResponseEntity<?> previewDocument(@PathVariable Integer id, HttpSession session) {
	    if (!isLoggedIn(session)) {
	        return ResponseEntity.notFound().build();
	    }

	    Optional<Document> documentOpt = documentRepository.findById(id);
	    if (!documentOpt.isPresent()) {
	        return ResponseEntity.notFound().build();
	    }

	    Document document = documentOpt.get();
	    Integer userId = (Integer) session.getAttribute("userId");

	    boolean isOwner = document.getUser().getId().equals(userId);
	    if (!isOwner && document.getStatus() != 2) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền xem tài liệu này.");
	    }

	    try {
	        Path filePath = Paths.get(document.getFilePath());
	        String ext = document.getFileType().toLowerCase();

	        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
	            return ResponseEntity.notFound().build();
	        }

	        // Image or PDF preview
	        if (Arrays.asList("png", "jpg", "jpeg", "gif", "pdf").contains(ext)) {
	            Resource resource = new UrlResource(filePath.toUri());
	            String contentType = Files.probeContentType(filePath);
	            if (contentType == null) contentType = "application/octet-stream";

	            return ResponseEntity.ok()
	                    .contentType(MediaType.parseMediaType(contentType))
	                    .body(resource);
	        }

	        // Text or Markdown preview
	        if (Arrays.asList("txt", "md").contains(ext)) {
	            String content = Files.readString(filePath, StandardCharsets.UTF_8);
	            return ResponseEntity.ok()
	                    .contentType(MediaType.TEXT_PLAIN)
	                    .body(content);
	        }

	        // Word (.docx)
	        if (ext.equals("docx")) {
	            try (XWPFDocument docx = new XWPFDocument(Files.newInputStream(filePath))) {
	                StringBuilder sb = new StringBuilder();
	                for (XWPFParagraph para : docx.getParagraphs()) {
	                    sb.append(para.getText()).append("<br>");
	                }
	                return ResponseEntity.ok()
	                        .contentType(MediaType.TEXT_HTML)
	                        .body(sb.toString());
	            }
	        }

	        // Excel (.xlsx, .xls)
	        if (ext.equals("xlsx") || ext.equals("xls")) {
	            try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(filePath))) {
	                StringBuilder html = new StringBuilder("<table border='1'>");
	                Sheet sheet = workbook.getSheetAt(0);
	                for (Row row : sheet) {
	                    html.append("<tr>");
	                    for (Cell cell : row) {
	                        html.append("<td>").append(cell.toString()).append("</td>");
	                    }
	                    html.append("</tr>");
	                }
	                html.append("</table>");

	                return ResponseEntity.ok()
	                        .contentType(MediaType.TEXT_HTML)
	                        .body(html.toString());
	            }
	        }

	        // PowerPoint (.pptx)
	        if (ext.equals("pptx")) {
	            try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(filePath))) {
	                StringBuilder html = new StringBuilder();
	                for (XSLFSlide slide : ppt.getSlides()) {
	                    html.append("<h3>Slide ").append(slide.getSlideNumber()).append("</h3>");
	                    for (XSLFShape shape : slide.getShapes()) {
	                        if (shape instanceof XSLFTextShape) {
	                            html.append(((XSLFTextShape) shape).getText()).append("<br>");
	                        }
	                    }
	                    html.append("<hr>");
	                }
	                return ResponseEntity.ok()
	                        .contentType(MediaType.TEXT_HTML)
	                        .body(html.toString());
	            }
	        }

	        // Nếu không hỗ trợ định dạng
	        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	                .body("Định dạng tệp này không thể xem trước. Vui lòng tải về.");

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("Có lỗi xảy ra khi xem trước tài liệu.");
	    }
	}

    
	
	private boolean isLoggedIn(HttpSession session) {
		return session.getAttribute("loggedInUser") != null;
	}
}
