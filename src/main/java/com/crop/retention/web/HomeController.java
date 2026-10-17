package com.crop.retention.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Signed-in users land on the dashboard; "/" simply forwards there. */
    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }
}
