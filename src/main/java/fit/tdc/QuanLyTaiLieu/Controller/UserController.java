package fit.tdc.QuanLyTaiLieu.Controller;

import fit.tdc.QuanLyTaiLieu.Model.User;
import fit.tdc.QuanLyTaiLieu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

@Controller
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    @PostConstruct
	public void createDefaultAdmin() {
	    String defaultAdminUsername = "admin";
	    String defaultAdminPassword = "123"; 
	    
	    if (userRepository.findByUsername(defaultAdminUsername).isEmpty()) {
	        User admin = new User();
	        admin.setUsername(defaultAdminUsername);
	        admin.setPassword(encodePassword(defaultAdminPassword));
	        admin.setRole("ADMIN");
	        userRepository.save(admin);
	        System.out.println("Tài khoản admin đã được tạo tự động.");
	    } else {
	        System.out.println("Tài khoản admin đã tồn tại.");
	    }
	}

    public String encodePassword(String password) {
		return passwordEncoder.encode(password);
	}

    // Hiển thị trang đăng nhập
    @GetMapping("/login")
    public String showLoginPage(Model model) {
        model.addAttribute("user", new User());
        return "login";
    }
    
    
    @PostMapping("/login")
    public String login(@ModelAttribute User user, HttpSession session, 
                       RedirectAttributes redirectAttributes) {
        Optional<User> foundUser = userRepository.findByUsername(user.getUsername());

        // ✅ So sánh bằng passwordEncoder.matches()
        if (foundUser.isPresent() && passwordEncoder.matches(user.getPassword(), foundUser.get().getPassword())) {
            User loggedInUser = foundUser.get();
            session.setAttribute("loggedInUser", loggedInUser);
            session.setAttribute("userId", loggedInUser.getId());
            session.setAttribute("username", loggedInUser.getUsername());
            session.setAttribute("role", loggedInUser.getRole());

            if ("ADMIN".equals(loggedInUser.getRole())) {
                return "redirect:/admin";
            } else {
                return "redirect:/documents";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng!");
            return "redirect:/login";
        }
    }

    
    // Hiển thị trang đăng ký
    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }
    
    // Xử lý đăng ký
    @PostMapping("/register")
    public String register(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        try {
            Optional<User> existingUser = userRepository.findByUsername(user.getUsername());
            if (existingUser.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Tên đăng nhập đã tồn tại!");
                return "redirect:/register";
            }

            // Mặc định role là USER
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("USER");
            }

            // ✅ Mã hóa mật khẩu
            user.setPassword(encodePassword(user.getPassword()));

            userRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Đăng ký thành công! Vui lòng đăng nhập.");
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra khi đăng ký!");
            return "redirect:/register";
        }
    }

    
    // Đăng xuất
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("message", "Đăng xuất thành công!");
        return "redirect:/login";
    }
    
    // Trang chủ - redirect đến login nếu chưa đăng nhập
    @GetMapping({"/", "/home"})
    public String home(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        
        if ("ADMIN".equals(loggedInUser.getRole())) {
            return "redirect:/admin";
        } else {
            return "redirect:/documents";
        }
    }
    
    // Kiểm tra phiên đăng nhập
    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("loggedInUser") != null;
    }
    
    // Kiểm tra quyền admin
    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return "ADMIN".equals(role);
    }
}