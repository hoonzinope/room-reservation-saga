package home.example.room_reserve_outer.controller;

import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationController {

    private final RoomService roomService;

    public ReservationController(RoomService roomService){
        this.roomService = roomService;
    }

    @GetMapping("/rooms/{room_number}/available")
    public RoomAvailability checkRoomAvailable(
            @PathVariable(value = "room_number", required = true) String room_number){
        return roomService.checkRoomAvailable(room_number);
    }

    @PostMapping("/reservation")
    public String createReservation() {
        return "ok";
    }

    @GetMapping("/reservation/{reservationId}")
    public String checkReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId) {
        return "ok";
    }

    @DeleteMapping("/reservation/{reservationId}")
    public String cancelReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId) {
        return "ok";
    }
}
