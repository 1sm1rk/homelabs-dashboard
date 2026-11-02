package de.homelabs.dashboard.shell;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The shell's home page: the layout around an empty content slot. Module pages are
 * added by the module packages; the shell itself renders no module data.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Overview");
        return "index";
    }
}
