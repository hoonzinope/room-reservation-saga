package home.example.room_reserve_outer.controller;

import home.example.room_reserve_outer.data.dto.ReservationRequest;
import home.example.room_reserve_outer.data.dto.ReservationResponse;
import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.service.ReservationService;
import home.example.room_reserve_outer.service.RoomService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationController {

    private final RoomService roomService;
    private final ReservationService reservationService;

    public ReservationController(RoomService roomService,
                                 ReservationService reservationService){
        this.roomService = roomService;
        this.reservationService = reservationService;
    }

    @GetMapping("/rooms/{room_number}/available")
    public RoomAvailability checkRoomAvailable(
            @PathVariable(value = "room_number", required = true) String room_number){
        return roomService.checkRoomAvailable(room_number);
    }

    @PostMapping("/reservation")
    public ReservationResponse createReservation(@RequestBody ReservationRequest reservationRequest) {
        return reservationService.book(reservationRequest);
    }

    @GetMapping("/reservation/{reservationId}")
    public ReservationResponse checkReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId) {
        return reservationService.checkBook(reservationId);
    }

    @DeleteMapping("/reservation/{reservationId}")
    public String cancelReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId) {
        return "ok";
    }
}
