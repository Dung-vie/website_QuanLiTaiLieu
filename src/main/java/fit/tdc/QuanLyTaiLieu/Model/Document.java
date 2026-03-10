package fit.tdc.QuanLyTaiLieu.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "filename")
	private String filename;

	@Column(name = "filepath")
	private String filePath;

	@Column(name = "size")
	private Long size;

	@Column(name = "filetype")
	private String fileType;

	@Column(name = "time_upload")
	private LocalDateTime timeUpload;
	
	@Column(name = "status")
	private int status = 0;

	public Document() {
		super();
	}

	public Document(Integer id, User user, String filename, String filePath, Long size, String fileType,
			LocalDateTime timeUpload, int status) {
		super();
		this.id = id;
		this.user = user;
		this.filename = filename;
		this.filePath = filePath;
		this.size = size;
		this.fileType = fileType;
		this.timeUpload = timeUpload;
		this.status = status;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public LocalDateTime getTimeUpload() {
		return timeUpload;
	}

	public void setTimeUpload(LocalDateTime timeUpload) {
		this.timeUpload = timeUpload;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

}
