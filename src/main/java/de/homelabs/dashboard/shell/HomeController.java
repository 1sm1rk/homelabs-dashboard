package de.homelabs.dashboard.shell;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The shell's home page: the layout around an empty content slot. Module pages are
 * added by the module packages; the shell itself renders no module data.
 *
 * <p>The page contributes no text of its own: its title and all chrome texts come from
 * the message bundles of {@link ShellI18nConfiguration}.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }
}
