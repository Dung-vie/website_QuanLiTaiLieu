# Hệ thống Quản Lý Tài Liệu

## Giới thiệu
Ứng dụng web backend hỗ trợ quản lý, lưu trữ và tìm kiếm tài liệu hiệu quả.  
Phù hợp cho môi trường học tập hoặc làm việc cần tổ chức tài liệu.

## Mục đích
- Quản lý tài liệu tập trung
- Lưu trữ và phân loại theo danh mục
- Tìm kiếm nhanh chóng, tiện lợi

## Chức năng tổng quát
- Người dùng đăng nhập và quản lý tài liệu
- Phân loại tài liệu theo danh mục
- Phân quyền cơ bản giữa người dùng và quản trị viên
- Tìm kiếm theo từ khóa

## Chức năng chính
| Chức năng            | Mô tả                                      |
|----------------------|---------------------------------------------|
| Xác thực người dùng  | Đăng ký và đăng nhập tài khoản              |
| Quản lý tài liệu     | Thêm, sửa, xoá và tìm kiếm tài liệu         |
| Phân loại            | Quản lý tài liệu theo danh mục              |
| Phân quyền           | Phân quyền cơ bản giữa user và admin        |
| Tìm kiếm             | Hiển thị danh sách và tìm kiếm theo từ khoá |

## Công nghệ sử dụng
| Thành phần  | Công nghệ                  |
|-------------|-----------------------------|
| Backend     | Java Spring / Servlet (MVC) |
| Database    | MySQL                       |
| Frontend    | HTML, CSS, JavaScript       |
| Server      | Apache Tomcat               |

##  Cách chạy dự án
1. Clone source code từ repository
2. Tạo database MySQL 
3. Cấu hình thông tin kết nối database
4. Deploy project lên Tomcat Server
5. Truy cập: http://localhost:8080/
