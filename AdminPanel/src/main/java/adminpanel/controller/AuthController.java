package adminpanel.controller;

import adminpanel.model.User;
import adminpanel.service.AdminManagementService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AdminManagementService managementService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            Model model) {

        Optional<User> userOpt = managementService.authenticate(username, password);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Записываем данные сессии на сервере! Извлечь их подменой заголовков теперь невозможно
            session.setAttribute("user", user.getUsername());
            session.setAttribute("hotelKey", user.getHotelKey());
            session.setAttribute("role", user.getRole());

            return "redirect:/admin/dashboard";
        } {
            model.addAttribute("error", "Неверный логин или пароль");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Уничтожаем сессию
        return "redirect:/login";
    }
}