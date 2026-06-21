package home.example.room_reserve_outer.controller;

import home.example.room_reserve_outer.data.entity.Reservation;
import home.example.room_reserve_outer.data.entity.Room;
import home.example.room_reserve_outer.repository.RecordRepository;
import home.example.room_reserve_outer.repository.ReservationRepository;
import home.example.room_reserve_outer.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "reservation.failure-rate=0")
@AutoConfigureMockMvc
@Transactional
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void setUp() {
        recordRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    void createReservationWithoutIdempotencyKey_returnsFailedResponse_andDoesNotPersistIdempotencyRecord() throws Exception {
        mockMvc.perform(post("/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReservationRequestJson(null, "101", "missing-idempotency-key")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_success").value("failed"))
                .andExpect(jsonPath("$.error_msg").value("param error"));

        assertThat(recordRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void checkReservationForCancelledReservation_returnsSuccess_andIncludesCancelledStatus() throws Exception {
        Reservation cancelledReservation = reservationRepository.save(buildCancelledReservation("101"));

        mockMvc.perform(get("/reservation/{reservationId}", cancelledReservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation_id").value((int) cancelledReservation.getId()))
                .andExpect(jsonPath("$.is_success").value("success"))
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void createReservationWithFailureRateZero_isDeterministic() throws Exception {
        List<String> roomNumbers = java.util.Arrays.asList("101", "102", "201", "301");

        for (int i = 0; i < roomNumbers.size(); i++) {
            String roomNumber = roomNumbers.get(i);
            mockMvc.perform(post("/reservation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createReservationRequestJson("deterministic-" + i, roomNumber,
                                    "deterministic-user-" + i)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_success").value("success"))
                .andExpect(jsonPath("$.error_msg").value(nullValue()));
        }

        assertThat(recordRepository.count()).isEqualTo(4L);
        assertThat(reservationRepository.count()).isEqualTo(4L);
    }

    @Test
    void checkRoomAvailability_usesReadLookupWithoutPessimisticLockFailure() throws Exception {
        mockMvc.perform(get("/rooms/{roomNumber}/available", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("101"))
                .andExpect(jsonPath("$.availability").value(true));
    }

    @Test
    void listRooms_returnsRoomNumberBasedRooms() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.room_number == '101')].room_type").value(hasItem("STANDARD")))
                .andExpect(jsonPath("$[?(@.room_number == '101')].availability").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.room_number == '202')].status").value(hasItem("MAINTENANCE")))
                .andExpect(jsonPath("$[?(@.room_number == '202')].availability").value(hasItem(false)));
    }

    @Test
    void cancelReservation_usesRequestBodyKeys() throws Exception {
        Reservation reservation = reservationRepository.save(buildConfirmedReservation("101"));

        mockMvc.perform(delete("/reservation/{reservationId}", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelReservationRequestJson("cancel-body-1", "create-body-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation_id").value((int) reservation.getId()))
                .andExpect(jsonPath("$.is_success").value("success"))
                .andExpect(jsonPath("$.status").value("cancelled"));

        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    private Reservation buildCancelledReservation(String roomNumber) {
        Reservation reservation = buildReservation(roomNumber, "cancelled");
        reservation.setCancelIdempotencyKey("cancel-cancelled-1");
        reservation.setCancelledAt(LocalDateTime.now());
        return reservation;
    }

    private Reservation buildConfirmedReservation(String roomNumber) {
        return buildReservation(roomNumber, "confirmed");
    }

    private Reservation buildReservation(String roomNumber, String status) {
        Room room = roomRepository.findAll().stream()
                .filter(candidate -> roomNumber.equals(candidate.getRoomNumber()))
                .findFirst()
                .orElseThrow(IllegalStateException::new);

        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = new Reservation();
        reservation.setRoomId(room.getId());
        reservation.setGuestName("cancelled-guest");
        reservation.setCheckInDate(LocalDate.of(2026, 7, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 7, 12));
        reservation.setStatus(status);
        reservation.setCreateIdempotencyKey("create-body-1");
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        return reservation;
    }

    private String createReservationRequestJson(String idempotencyKey, String roomNumber, String guestName) {
        return "{"
                + "\"idempotency_key\":" + toJsonValue(idempotencyKey) + ","
                + "\"room_number\":\"" + roomNumber + "\","
                + "\"guest_name\":\"" + guestName + "\","
                + "\"checkIn\":\"2026-07-01\","
                + "\"checkOut\":\"2026-07-03\","
                + "\"operation\":\"create_reservation\""
                + "}";
    }

    private String toJsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
    }

    private String cancelReservationRequestJson(String idempotencyKey, String createIdempotencyKey) {
        return "{"
                + "\"idempotency_key\":\"" + idempotencyKey + "\","
                + "\"create_idempotency_key\":\"" + createIdempotencyKey + "\""
                + "}";
    }
}
