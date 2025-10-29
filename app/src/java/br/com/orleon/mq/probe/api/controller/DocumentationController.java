package br.com.orleon.mq.probe.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocumentationController {

    @GetMapping({"/docs", "/docs/"})
    public String redirectToSwaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }
}

