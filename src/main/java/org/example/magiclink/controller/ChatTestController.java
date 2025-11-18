package org.example.magiclink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatTestController {

    @GetMapping("/chatroom-test")
    public String chatroomTest() {
        return "chatroom-test";
    }
}
