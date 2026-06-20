package home.example.room_reserve_outer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/ping")
    public ResponseEntity<String> ping(){
        return new ResponseEntity<>("pong",HttpStatus.OK);
    }

}
