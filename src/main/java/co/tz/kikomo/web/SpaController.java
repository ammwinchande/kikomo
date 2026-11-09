package co.tz.kikomo.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The admin portal is a single-page app with client-side routes; a hard reload on any of them must serve the
 * SPA shell. Only the known pages are forwarded so unknown URLs still 404.
 */
@Controller
public class SpaController {

    @GetMapping({"/login", "/approvals", "/activity", "/audit", "/policy", "/demo"})
    public String spa() {
        return "forward:/index.html";
    }
}
