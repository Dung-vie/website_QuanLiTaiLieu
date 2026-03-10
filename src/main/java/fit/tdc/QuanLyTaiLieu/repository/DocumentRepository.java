package fit.tdc.QuanLyTaiLieu.repository;

import fit.tdc.QuanLyTaiLieu.Model.Document;
import fit.tdc.QuanLyTaiLieu.Model.User;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public interface DocumentRepository extends JpaRepository<Document, Integer> {
	List<Document> findByFilenameContaining(String keyword); // Tìm kiếm tài liệu theo tên
	List<Document> findByFileType(String fileType);  // Lọc theo loại file
    List<Document> findByFilenameContainingAndFileType(String filename, String fileType);  // Tìm kiếm và lọc theo tên và loại file
    List<Document> findByUser(User user);
    List<Document> findByUserAndFilenameContaining(User user, String filename);
    List<Document> findByUserAndFileType(User user, String fileType);
    List<Document> findByUserAndFilenameContainingAndFileType(User user, String filename, String fileType);
    List<Document> findByFilenameContainingAndFileTypeAndStatus(String filename, String fileType, Integer status);
    List<Document> findByFilenameContainingAndStatus(String filename, Integer status);
    List<Document> findByFileTypeAndStatus(String fileType, Integer status);
    List<Document> findByStatus(int status);
    boolean existsByFilenameAndUserId(String filename, Integer userId);
    
    Page<Document> findByFilenameContaining(String keyword, Pageable pageable);
    Page<Document> findByFileType(String fileType, Pageable pageable);
    Page<Document> findByFilenameContainingAndFileType(String filename, String fileType, Pageable pageable);
    Page<Document> findByUser(User user, Pageable pageable);
    Page<Document> findByUserAndFilenameContaining(User user, String filename, Pageable pageable);
    Page<Document> findByUserAndFileType(User user, String fileType, Pageable pageable);
    Page<Document> findByUserAndFilenameContainingAndFileType(User user, String filename, String fileType, Pageable pageable);
    Page<Document> findByUserIdOrStatusIn(Long userId, List<Integer> statuses, Pageable pageable);
    Page<Document> findByFilenameContainingAndFileTypeAndStatus(String filename, String fileType, Integer status, Pageable pageable);
    Page<Document> findByUserIdOrStatus(Long userId, Integer status, Pageable pageable);
    Page<Document> findByFilenameContainingIgnoreCase(String filename, Pageable pageable);
    Page<Document> findByFileTypeIgnoreCase(String fileType, Pageable pageable);
    Page<Document> findAll(Pageable pageable);
    Page<Document> findByStatus(int status, Pageable pageable);
    Page<Document> findByFilenameContainingIgnoreCaseAndStatusIn(String filename, List<Integer> status, Pageable pageable);
    Page<Document> findByFileTypeIgnoreCaseAndStatusIn(String fileType, List<Integer> status, Pageable pageable);
    Page<Document> findByUserAndStatusIn(User user, List<Integer> status, Pageable pageable);
    Page<Document> findByStatusIn(List<Integer> status, Pageable pageable);


}
