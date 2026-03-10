package fit.tdc.QuanLyTaiLieu.Controller;

import fit.tdc.QuanLyTaiLieu.Model.Document;
import fit.tdc.QuanLyTaiLieu.Model.User;
import fit.tdc.QuanLyTaiLieu.repository.DocumentRepository;
import fit.tdc.QuanLyTaiLieu.repository.UserRepository;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


@Controller
@RequestMapping("/admin")
public class AdminController {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Value("${upload.dir}")
    private String uploadDir;
    
    @GetMapping({"", "/dashboard"})
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        // Thống kê cơ bản
        long userCount = userRepository.count(); // đổi tên để khớp template
        long documentsCount = documentRepository.count(); // đổi tên
        long publicDocuments = documentRepository.findByStatus(2).size();
        long privateDocuments = documentsCount - publicDocuments;

        // Thêm vào model với tên giống file HTML
        model.addAttribute("userCount", userCount);
        model.addAttribute("documentsCount", documentsCount);

        List<Document> allDocuments = documentRepository.findAll();
        
        // FIX: Xử lý trường hợp Document có size null
        double totalSizeMB = allDocuments.stream()
            .filter(doc -> doc.getSize() != null) // Lọc bỏ các document có size null
            .mapToDouble(Document::getSize) // trả về MB
            .sum();

        double totalSizeGB = totalSizeMB / 1024.0;

        model.addAttribute("totalSize", String.format("%.2f GB", totalSizeGB));

        // Đếm số loại file (fileType) - cũng cần xử lý null
        long fileTypesCount = allDocuments.stream()
            .map(Document::getFileType)
            .filter(fileType -> fileType != null && !fileType.isEmpty()) // Lọc bỏ fileType null hoặc rỗng
            .distinct()
            .count();
        model.addAttribute("fileTypesCount", fileTypesCount);

        // Danh sách tài liệu gần đây
        allDocuments.sort((d1, d2) -> {
            // Xử lý trường hợp timeUpload có thể null
            if (d1.getTimeUpload() == null && d2.getTimeUpload() == null) return 0;
            if (d1.getTimeUpload() == null) return 1;
            if (d2.getTimeUpload() == null) return -1;
            return d2.getTimeUpload().compareTo(d1.getTimeUpload());
        });
        
        if (allDocuments.size() > 5) {
            allDocuments = allDocuments.subList(0, 5);
        }
        model.addAttribute("documents", allDocuments);

        return "admin/index";
    }

    @GetMapping("/documents")
    public String manageDocuments(HttpSession session,
                                  Model model,
                                  @RequestParam(value = "search", required = false) String search,
                                  @RequestParam(value = "filterType", required = false) String filterType,
                                  @RequestParam(value = "userId", required = false) Integer userId,
                                  @RequestParam(value = "page", defaultValue = "1") int page) {
        if (!isAdmin(session)) return "redirect:/login";

        int pageSize = 10;
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        Page<Document> documentPage;
        List<Integer> allowedStatuses = Arrays.asList(0, 2); 

        if (search != null && !search.isEmpty()) {
            documentPage = documentRepository.findByFilenameContainingIgnoreCaseAndStatusIn(search, allowedStatuses, pageable);
        } else if (filterType != null && !filterType.isEmpty()) {
            documentPage = documentRepository.findByFileTypeIgnoreCaseAndStatusIn(filterType, allowedStatuses, pageable);
        } else if (userId != null) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                documentPage = documentRepository.findByUserAndStatusIn(userOpt.get(), allowedStatuses, pageable);
            } else {
                documentPage = documentRepository.findByStatusIn(allowedStatuses, pageable);
            }
        } else {
            documentPage = documentRepository.findByStatusIn(allowedStatuses, pageable);
        }

        model.addAttribute("documents", documentPage.getContent());
        model.addAttribute("totalPages", documentPage.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("search", search);
        model.addAttribute("filterType", filterType);
        model.addAttribute("userId", userId);
        model.addAttribute("users", userRepository.findAll());

        return "admin/documents";
    }

    // Xóa tài liệu
    @PostMapping("/documents/delete/{id}")
    public String deleteDocument(@PathVariable Integer id, HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        
        Optional<Document> documentOpt = documentRepository.findById(id);
        if (!documentOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy tài liệu!");
            return "redirect:/admin/documents";
        }
        
        Document document = documentOpt.get();
        
        try {
            // Xóa file vật lý
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
            
            // Xóa record trong database
            documentRepository.delete(document);
            
            redirectAttributes.addFlashAttribute("success", "Xóa tài liệu thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra khi xóa tài liệu!");
        }
        
        return "redirect:/admin/documents";
    }
    
    @GetMapping("/documents/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        Optional<Document> optionalDoc = documentRepository.findById(id);
        if (optionalDoc.isEmpty()) {
            model.addAttribute("error", "Không tìm thấy tài liệu.");
            return "redirect:/admin/documents";
        }

        List<User> users = userRepository.findAll(); // danh sách user để chọn trong dropdown
        model.addAttribute("document", optionalDoc.get());
        model.addAttribute("users", users);
        return "admin/edit"; 
    }
    
    @PostMapping("/documents/edit/{id}")
    public String updateDocument(@PathVariable Integer id,
                                 @RequestParam("filename") String filename,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam("userId") int userId,
                                 @RequestParam("status") int status,
                                 RedirectAttributes redirectAttributes) {

        Optional<Document> optionalDoc = documentRepository.findById(id);
        if (optionalDoc.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Tài liệu không tồn tại.");
            return "redirect:/admin/documents";
        }

        Document document = optionalDoc.get();

        // Cập nhật tên tài liệu
        document.setFilename(filename);

        // Cập nhật trạng thái (chỉ cho 0 và 2)
        if (status == 0 || status == 2) {
            document.setStatus(status);
        }

        // Nếu có file mới thì ghi đè và cập nhật thông tin file
        if (!file.isEmpty()) {
            try {
                String originalFilename = file.getOriginalFilename();
                int dotIndex = originalFilename.lastIndexOf('.');
                String filetype = dotIndex == -1 ? "" : originalFilename.substring(dotIndex + 1);

                Path filePath = Paths.get(uploadDir, originalFilename);

                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                document.setFileType(filetype);
                document.setFilePath(filePath.toString());

            } catch (IOException e) {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("error", "Không thể cập nhật file mới.");
                return "redirect:/admin/documents/edit/" + id;
            }
        }

        // Không cho thay đổi người dùng: chỉ gán lại người nếu chưa có
        if (document.getUser() == null) {
            userRepository.findById(userId).ifPresent(document::setUser);
        }

        // Cập nhật thời gian upload
        document.setTimeUpload(LocalDateTime.now());

        documentRepository.save(document);

        redirectAttributes.addFlashAttribute("message", "Cập nhật tài liệu thành công.");
        return "redirect:/admin/documents";
    }


    @GetMapping("/documents/download/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Integer id, HttpSession session) {
        if (!isLoggedIn(session)) return ResponseEntity.notFound().build();

        Optional<Document> docOpt = documentRepository.findById(id);
        if (!docOpt.isPresent()) return ResponseEntity.notFound().build();

        Document doc = docOpt.get();
        Integer userId = (Integer) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");

        // Nếu không phải Admin và file là private (status=0) thì chỉ được tải nếu là chủ sở hữu
        if (!"ADMIN".equals(role) && doc.getStatus() == 0 && !doc.getUser().getId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path path = Paths.get(doc.getFilePath());
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = Files.probeContentType(path);
                if (contentType == null) {
                    contentType = "application/octet-stream";
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

    
     @GetMapping("/documents/status")
    public String pendingDocuments(HttpSession session, Model model,
                                   @RequestParam(value = "page", defaultValue = "1") int page) {
        if (!isAdmin(session)) return "redirect:/login";

        int pageSize = 5;
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        Page<Document> pendingPage = documentRepository.findByStatus(1, pageable);

        model.addAttribute("documents", pendingPage.getContent());
        model.addAttribute("totalPages", pendingPage.getTotalPages());
        model.addAttribute("currentPage", page);

        return "admin/status"; 
    }
     
    // Duyệt tài liệu - chuyển từ private sang public
     @PostMapping("/documents/approve/{id}")
     public String approveDocument(@PathVariable Integer id, HttpSession session,
                                   RedirectAttributes redirectAttributes) {
         if (!isAdmin(session)) return "redirect:/login";

         Optional<Document> documentOpt = documentRepository.findById(id);
         if (!documentOpt.isPresent()) {
             redirectAttributes.addFlashAttribute("error", "Không tìm thấy tài liệu!");
             return "redirect:/admin/documents/pending";
         }

         Document document = documentOpt.get();
         if (document.getStatus() != 1) {
             redirectAttributes.addFlashAttribute("error", "Tài liệu không ở trạng thái chờ duyệt!");
             return "redirect:/admin/documents/status";
         }

         document.setStatus(2); // chuyển sang công khai
         documentRepository.save(document);

         redirectAttributes.addFlashAttribute("success", "Đã duyệt tài liệu thành công!");
         return "redirect:/admin/documents/status";
     }

     @PostMapping("/documents/unapprove/{id}")
     public String unapproveDocument(@PathVariable Integer id, HttpSession session,
                                     RedirectAttributes redirectAttributes) {
         if (!isAdmin(session)) {
             return "redirect:/login";
         }

         Optional<Document> documentOpt = documentRepository.findById(id);
         if (!documentOpt.isPresent()) {
             redirectAttributes.addFlashAttribute("error", "Không tìm thấy tài liệu!");
             return "redirect:/admin/documents";
         }

         Document document = documentOpt.get();
         int status = document.getStatus();
         if (status == 0) {
             redirectAttributes.addFlashAttribute("error", "Tài liệu đã ở trạng thái riêng tư!");
             return "redirect:/admin/documents";
         }

         // Nếu đang ở trạng thái 1 hoặc 2 thì chuyển về 0
         document.setStatus(0);
         documentRepository.save(document);

         redirectAttributes.addFlashAttribute("success", "Đã hủy duyệt tài liệu!");
         return "redirect:/admin/documents";
     }

     @GetMapping("/documents/preview/{id}")
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

     
    // Xem chi tiết tài liệu
    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Integer id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        
        Optional<Document> documentOpt = documentRepository.findById(id);
        if (!documentOpt.isPresent()) {
            return "redirect:/admin/documents";
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String formattedTime = documentOpt.get().getTimeUpload().format(formatter);

        model.addAttribute("document", documentOpt.get());
        model.addAttribute("formattedTimeUpload", formattedTime);
        return "admin/detail";
    }
    
    
    // Quản lý người dùng
    @GetMapping("/users")
    public String manageUsers(HttpSession session, Model model,
                              @RequestParam(required = false) String search) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        List<User> users;
        if (search != null && !search.isEmpty()) {
        	users = userRepository.findAll().stream()
                    .filter(user -> user.getUsername().toLowerCase().contains(search.toLowerCase()))
                    .toList();
        } else {
            users = userRepository.findAll();
        }

        // Tạo map lưu tổng số tài liệu của từng user
        Map<Integer, Long> fileCounts = new HashMap<>();
        // Tạo map lưu tổng số kiểu file khác nhau của từng user
        Map<Integer, Long> fileTypeCounts = new HashMap<>();

        for (User user : users) {
            List<Document> docs = documentRepository.findByUser(user);
            fileCounts.put(user.getId(), (long) docs.size());

            // Đếm số kiểu file khác nhau
            long typeCount = docs.stream()
                .map(Document::getFileType) // giả sử Document có getFileType()
                .filter(Objects::nonNull)
                .distinct()
                .count();

            fileTypeCounts.put(user.getId(), typeCount);
        }

        model.addAttribute("users", users);
        model.addAttribute("fileCounts", fileCounts);
        model.addAttribute("fileTypeCounts", fileTypeCounts);
        model.addAttribute("search", search);

        return "admin/users";
    }

    
    // Xóa người dùng
    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Integer id, HttpSession session,
                           RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        
        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng!");
            return "redirect:/admin/users";
        }
        
        User user = userOpt.get();
        Integer currentUserId = (Integer) session.getAttribute("userId");
        
        // Không được xóa chính mình
        if (user.getId().equals(currentUserId)) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa tài khoản của chính mình!");
            return "redirect:/admin/users";
        }
        
        try {
            // Xóa tất cả tài liệu của user
            List<Document> userDocuments = documentRepository.findByUser(user);
            for (Document doc : userDocuments) {
                try {
                    Path filePath = Paths.get(doc.getFilePath());
                    Files.deleteIfExists(filePath);
                } catch (Exception e) {
                    // Log error nhưng tiếp tục xóa
                }
            }
            documentRepository.deleteAll(userDocuments);
            
            // Xóa user
            userRepository.delete(user);
            
            redirectAttributes.addFlashAttribute("success", "Xóa người dùng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra khi xóa người dùng!");
        }
        
        return "redirect:/admin/users";
    }
    
    // Thay đổi role của user
    @PostMapping("/users/role/{id}")
    public String changeUserRole(@PathVariable Integer id, 
                               @RequestParam String newRole,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        
        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng!");
            return "redirect:/admin/users";
        }
        
        User user = userOpt.get();
        Integer currentUserId = (Integer) session.getAttribute("userId");
        
        // Không được thay đổi role của chính mình
        if (user.getId().equals(currentUserId)) {
            redirectAttributes.addFlashAttribute("error", "Không thể thay đổi quyền của chính mình!");
            return "redirect:/admin/users";
        }
        
        user.setRole(newRole);
        userRepository.save(user);
        
        redirectAttributes.addFlashAttribute("success", "Thay đổi quyền người dùng thành công!");
        return "redirect:/admin/users";
    }
    
    @GetMapping("/documents/uploads")
    public String showUploadForm(Model model, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        
        model.addAttribute("users", userRepository.findAll());
        return "admin/uploads";
    }

    @PostMapping("/documents/uploads")
    public String uploadDocuments(@RequestParam("files") MultipartFile[] files,
                                  @RequestParam("assignUser") Integer userId,
                                  @RequestParam("status") int status,
                                  RedirectAttributes redirectAttributes,
                                  HttpSession session) {

        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        if (files == null || files.length == 0) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ít nhất một file để upload!");
            return "redirect:/admin/documents/uploads";
        }

        long totalSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 10 * 1024 * 1024) {
            redirectAttributes.addFlashAttribute("error", "Tổng dung lượng không được vượt quá 10MB!");
            return "redirect:/admin/documents/uploads";
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Người dùng không tồn tại.");
            return "redirect:/admin/documents/uploads";
        }

        User user = userOpt.get();

        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.trim().isEmpty()) continue;

                String filenameNoExt = originalFilename.contains(".")
                        ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
                        : originalFilename;
                String extension = originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1)
                        : "";

                if (documentRepository.existsByFilenameAndUserId(filenameNoExt, user.getId())) {
                    // Lưu file tạm vào uploads/temp thay vì session
                    Path tempDir = Paths.get(uploadDir, "temp");
                    if (!Files.exists(tempDir)) {
                        Files.createDirectories(tempDir);
                    }

                    String tempFilename = UUID.randomUUID() + "_" + originalFilename;
                    Path tempPath = tempDir.resolve(tempFilename);
                    try (InputStream is = file.getInputStream()) {
                        Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    session.setAttribute("tempSavedFilename", tempFilename);
                    session.setAttribute("tempOriginalFilename", originalFilename);
                    session.setAttribute("tempUserId", user.getId());
                    session.setAttribute("tempStatus", status);

                    redirectAttributes.addFlashAttribute("fileName", filenameNoExt);
                    redirectAttributes.addFlashAttribute("originalFilename", originalFilename);
                    return "redirect:/admin/documents/confirm-upload";
                }

                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                Path filePath = uploadPath.resolve(originalFilename);
                try (InputStream is = file.getInputStream()) {
                    Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                Document doc = new Document();
                doc.setFilename(filenameNoExt);
                doc.setFileType(extension);
                doc.setFilePath(filePath.toString());
                doc.setSize(file.getSize());
                doc.setTimeUpload(LocalDateTime.now());
                doc.setUser(user);
                doc.setStatus(status);

                documentRepository.save(doc);
            }

            redirectAttributes.addFlashAttribute("success",
                    "Upload thành công " + files.length + " tài liệu cho user " + user.getUsername() + "!");
            return "redirect:/admin/documents/uploads";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi khi upload: " + e.getMessage());
            return "redirect:/admin/documents/uploads";
        }
    }

    @GetMapping("/documents/confirm-upload")
    public String showConfirmUpload(Model model, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/login";
        return "admin/confirm-upload";
    }

    @PostMapping("/documents/confirm-upload")
    public String handleConfirmUpload(@RequestParam("action") String action,
                                      @RequestParam(value = "newFilename", required = false) String newFilename,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/login";

        String tempFilename = (String) session.getAttribute("tempSavedFilename");
        String originalFilename = (String) session.getAttribute("tempOriginalFilename");
        Integer userId = (Integer) session.getAttribute("tempUserId");
        Integer status = (Integer) session.getAttribute("tempStatus");

        if (tempFilename == null || originalFilename == null || userId == null || status == null) {
            redirectAttributes.addFlashAttribute("error", "Thông tin xác nhận không hợp lệ.");
            return "redirect:/admin/documents/uploads";
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Người dùng không tồn tại.");
            return "redirect:/admin/documents/uploads";
        }
        User user = userOpt.get();

        Path tempFilePath = Paths.get(uploadDir, "temp", tempFilename);
        if (!Files.exists(tempFilePath)) {
            redirectAttributes.addFlashAttribute("error", "File tạm không tồn tại.");
            clearTempSession(session);
            return "redirect:/admin/documents/uploads";
        }

        if (action.equals("cancel")) {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            clearTempSession(session);
            redirectAttributes.addFlashAttribute("warning", "Đã hủy upload file.");
            return "redirect:/admin/documents/uploads";
        }

        if (action.equals("upload")) {
            try {
                String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String finalFilename = newFilename + "." + extension;
                Path finalFilePath = uploadPath.resolve(finalFilename);

                Files.move(tempFilePath, finalFilePath, StandardCopyOption.REPLACE_EXISTING);

                Document doc = new Document();
                doc.setFilename(newFilename);
                doc.setFileType(extension);
                doc.setFilePath(finalFilePath.toString());
                doc.setSize(Files.size(finalFilePath));
                doc.setTimeUpload(LocalDateTime.now());
                doc.setUser(user);
                doc.setStatus(status);

                documentRepository.save(doc);
                redirectAttributes.addFlashAttribute("success", "Upload thành công với tên mới: " + newFilename);

            } catch (IOException e) {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("error", "Lỗi khi upload: " + e.getMessage());
            }

            clearTempSession(session);
            return "redirect:/admin/documents/uploads";
        }

        redirectAttributes.addFlashAttribute("error", "Hành động không hợp lệ.");
        return "redirect:/admin/documents/uploads";
    }

    private void clearTempSession(HttpSession session) {
        session.removeAttribute("tempSavedFilename");
        session.removeAttribute("tempOriginalFilename");
        session.removeAttribute("tempUserId");
        session.removeAttribute("tempStatus");
    }

    
    @GetMapping("/roles")
    public String roleManagementPage(Model model, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        List<User> allUsers = userRepository.findAll();
        model.addAttribute("users", allUsers);

        return "admin/roles"; // trỏ về template `roles.html`
    }

    
    @PostMapping("/roles")
    public String updateUserRole(@RequestParam("userId") Integer userId,
                                 @RequestParam("newRole") String newRole,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập.");
            return "redirect:/login";
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng.");
            return "redirect:/admin/roles";
        }

        User user = userOpt.get();

        // Chỉ cho phép ADMIN / USER
        if (!"USER".equals(newRole) && !"ADMIN".equals(newRole)) {
            redirectAttributes.addFlashAttribute("error", "Vai trò không hợp lệ.");
            return "redirect:/admin/roles";
        }

        user.setRole(newRole);
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("success", "Đã cập nhật vai trò người dùng: " + user.getUsername());
        return "redirect:/admin/roles";
    }
    
    // Kiểm tra quyền admin
    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return "ADMIN".equals(role);
    }
    
    // Kiểm tra đăng nhập
    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("loggedInUser") != null;
    }
}