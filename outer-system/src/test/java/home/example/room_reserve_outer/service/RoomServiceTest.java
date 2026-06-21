package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.RoomAvailability;
import home.example.room_reserve_outer.data.dto.RoomResponse;
import home.example.room_reserve_outer.data.entity.Reservation;
import home.example.room_reserve_outer.data.entity.Room;
import home.example.room_reserve_outer.data.type.Status;
import home.example.room_reserve_outer.repository.ReservationRepository;
import home.example.room_reserve_outer.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "reservation.failure-rate=0",
        "spring.datasource.url=jdbc:h2:mem:outer_room_service_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@Transactional
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void checkRoomAvailable_availableRoom_returnsAvailableMessage() {
        RoomAvailability response = roomService.checkRoomAvailable("101");

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isTrue();
        assertThat(response.getMsg()).isEqualTo("room is available");
    }

    @Test
    void checkRoomAvailable_maintenanceRoom_returnsMaintenanceMessage() {
        RoomAvailability response = roomService.checkRoomAvailable("202");

        assertThat(response.getRoomNumber()).isEqualTo("202");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("room is maintenance");
    }

    @Test
    void checkRoomAvailable_unknownRoom_returnsNotExistMessage() {
        RoomAvailability response = roomService.checkRoomAvailable("999");

        assertThat(response.getRoomNumber()).isEqualTo("999");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("room not exist");
    }

    @Test
    void checkRoomAvailability_availableRoomWithoutOverlappingReservation_returnsAvailable() {
        RoomAvailability response = roomService.checkRoomAvailability(
                "101",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3));

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isTrue();
        assertThat(response.getMsg()).isEqualTo("room is available");
    }

    @Test
    void checkRoomAvailability_overlappingConfirmedReservation_returnsReserved() {
        saveReservation("101", Status.CONFIRMED.getCode(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3));

        RoomAvailability response = roomService.checkRoomAvailability(
                "101",
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 4));

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("room is already reserved");
    }

    @Test
    void checkRoomAvailability_overlappingCancelledReservation_returnsAvailable() {
        saveReservation("101", Status.CANCELLED.getCode(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3));

        RoomAvailability response = roomService.checkRoomAvailability(
                "101",
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 4));

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isTrue();
        assertThat(response.getMsg()).isEqualTo("room is available");
    }

    @Test
    void checkRoomAvailability_adjacentConfirmedReservation_returnsAvailable() {
        saveReservation("101", Status.CONFIRMED.getCode(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3));

        RoomAvailability response = roomService.checkRoomAvailability(
                "101",
                LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 7, 5));

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isTrue();
        assertThat(response.getMsg()).isEqualTo("room is available");
    }

    @Test
    void checkRoomAvailability_invalidDate_returnsCheckDateError() {
        RoomAvailability response = roomService.checkRoomAvailability(
                "101",
                LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 7, 1));

        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("check date error");
    }

    @Test
    void checkRoomAvailability_maintenanceRoom_returnsMaintenance() {
        RoomAvailability response = roomService.checkRoomAvailability(
                "202",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3));

        assertThat(response.getRoomNumber()).isEqualTo("202");
        assertThat(response.getAvailability()).isFalse();
        assertThat(response.getMsg()).isEqualTo("room is maintenance");
    }

    @Test
    void findRooms_returnsRoomNumberBasedSummariesWithoutInternalIds() {
        List<RoomResponse> response = roomService.findRooms();

        assertThat(response).hasSize(5);
        assertThat(response)
                .extracting(RoomResponse::getRoom_number)
                .containsExactlyInAnyOrder("101", "102", "201", "202", "301");
        assertThat(response)
                .filteredOn(room -> "101".equals(room.getRoom_number()))
                .singleElement()
                .satisfies(room -> {
                    assertThat(room.getRoom_type()).isEqualTo("STANDARD");
                    assertThat(room.getStatus()).isEqualTo("AVAILABLE");
                    assertThat(room.getAvailability()).isTrue();
                });
        assertThat(response)
                .filteredOn(room -> "202".equals(room.getRoom_number()))
                .singleElement()
                .satisfies(room -> {
                    assertThat(room.getRoom_type()).isEqualTo("DELUXE");
                    assertThat(room.getStatus()).isEqualTo("MAINTENANCE");
                    assertThat(room.getAvailability()).isFalse();
                });
    }

    private Reservation saveReservation(String roomNumber, String status, LocalDate checkIn, LocalDate checkOut) {
        Room room = roomRepository.findAll().stream()
                .filter(candidate -> roomNumber.equals(candidate.getRoomNumber()))
                .findFirst()
                .orElseThrow(AssertionError::new);

        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = new Reservation();
        reservation.setRoomId(room.getId());
        reservation.setGuestName("guest");
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkOut);
        reservation.setStatus(status);
        reservation.setCreateIdempotencyKey("room-service-test-" + status + "-" + checkIn);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        if(Status.CANCELLED.getCode().equals(status)) {
            reservation.setCancelledAt(now);
        }
        return reservationRepository.save(reservation);
    }
}
