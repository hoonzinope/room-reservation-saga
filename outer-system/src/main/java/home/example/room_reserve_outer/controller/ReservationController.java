package home.example.room_reserve_outer.controller;

import home.example.room_reserve_outer.data.dto.ReservationRequest;
import home.example.room_reserve_outer.data.dto.ReservationCancelRequest;
import home.example.room_reserve_outer.data.dto.ReservationResponse;
import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.service.RandomFailureSimulator;
import home.example.room_reserve_outer.service.ReservationService;
import home.example.room_reserve_outer.service.RoomService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationController {

    private final RoomService roomService;
    private final ReservationService reservationService;
    private final RandomFailureSimulator randomFailureSimulator;

    public ReservationController(RoomService roomService,
                                 ReservationService reservationService,
                                 RandomFailureSimulator randomFailureSimulator){
        this.roomService = roomService;
        this.reservationService = reservationService;
        this.randomFailureSimulator = randomFailureSimulator;
    }

    @GetMapping("/rooms/{room_number}/available")
    public RoomAvailability checkRoomAvailable(
            @PathVariable(value = "room_number", required = true) String room_number){
        RoomAvailability response = roomService.checkRoomAvailable(room_number);
        randomFailureSimulator.maybeFail();
        return response;
    }

    @PostMapping("/reservation")
    public ReservationResponse createReservation(@RequestBody ReservationRequest reservationRequest) {
        ReservationResponse response = reservationService.book(reservationRequest);
        randomFailureSimulator.maybeFail();
        return response;
    }

    @GetMapping("/reservation/{reservationId}")
    public ReservationResponse checkReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId) {
        ReservationResponse response = reservationService.checkBook(reservationId);
        randomFailureSimulator.maybeFail();
        return response;
    }

    @DeleteMapping("/reservation/{reservationId}")
    public ReservationResponse cancelReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId,
            @RequestBody(required = false) ReservationCancelRequest cancelRequest) {
        ReservationResponse response = reservationService.cancelBook(
                reservationId,
                cancelRequest == null ? null : cancelRequest.getIdempotency_key(),
                cancelRequest == null ? null : cancelRequest.getCreate_idempotency_key());
        randomFailureSimulator.maybeFail();
        return response;
    }
}
