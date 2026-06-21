package home.example.room_reserve_outer.controller;

import home.example.room_reserve_outer.data.dto.ReservationRequest;
import home.example.room_reserve_outer.data.dto.ReservationCancelRequest;
import home.example.room_reserve_outer.data.dto.ReservationResponse;
import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.data.type.ReservationResult;
import home.example.room_reserve_outer.service.RandomFailureSimulator;
import home.example.room_reserve_outer.service.ReservationService;
import home.example.room_reserve_outer.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
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
        log.info("room availability request roomNumber={}", room_number);
        RoomAvailability response = roomService.checkRoomAvailable(room_number);
        log.info("room availability processed roomNumber={} availability={} message={}",
                response.getRoomNumber(), response.getAvailability(), response.getMsg());
        maybeSimulateFailure("check-room-availability");
        return response;
    }

    @PostMapping("/reservation")
    public ReservationResponse createReservation(@RequestBody ReservationRequest reservationRequest) {
        log.info("reservation create request roomNumber={} operation={}",
                reservationRequest == null ? null : reservationRequest.getRoom_number(),
                reservationRequest == null ? null : reservationRequest.getOperation());
        ReservationResponse response = reservationService.book(reservationRequest);
        logReservationResponse("create-reservation", response);
        maybeSimulateFailure("create-reservation");
        return response;
    }

    @GetMapping("/reservation/{reservationId}")
    public ReservationResponse checkReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId) {
        log.info("reservation check request reservationId={}", reservationId);
        ReservationResponse response = reservationService.checkBook(reservationId);
        logReservationResponse("check-reservation", response);
        maybeSimulateFailure("check-reservation");
        return response;
    }

    @DeleteMapping("/reservation/{reservationId}")
    public ReservationResponse cancelReservation(
            @PathVariable(value = "reservationId", required = true) long reservationId,
            @RequestBody(required = false) ReservationCancelRequest cancelRequest) {
        log.info("reservation cancel request reservationId={} hasBody={}", reservationId, cancelRequest != null);
        ReservationResponse response = reservationService.cancelBook(
                reservationId,
                cancelRequest == null ? null : cancelRequest.getIdempotency_key(),
                cancelRequest == null ? null : cancelRequest.getCreate_idempotency_key());
        logReservationResponse("cancel-reservation", response);
        maybeSimulateFailure("cancel-reservation");
        return response;
    }

    private void logReservationResponse(String action, ReservationResponse response) {
        if(response == null) {
            log.warn("reservation processed action={} result=null", action);
            return;
        }

        if(response.getIs_success() == ReservationResult.SUCCESS) {
            log.info("reservation processed action={} result={} reservationId={} roomNumber={} operation={} status={}",
                    action,
                    response.getIs_success(),
                    response.getReservation_id(),
                    response.getRoom_number(),
                    response.getOperation(),
                    response.getStatus());
            return;
        }

        log.warn("reservation processed action={} result={} reservationId={} roomNumber={} operation={} status={} error={}",
                action,
                response.getIs_success(),
                response.getReservation_id(),
                response.getRoom_number(),
                response.getOperation(),
                response.getStatus(),
                response.getError_msg());
    }

    private void maybeSimulateFailure(String action) {
        try {
            randomFailureSimulator.maybeFail();
        } catch (RuntimeException e) {
            log.warn("reservation API simulated failure action={} error={}", action, e.getMessage());
            throw e;
        }
    }
}
