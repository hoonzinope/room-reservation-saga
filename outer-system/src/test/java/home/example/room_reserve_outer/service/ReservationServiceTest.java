package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.ReservationRequest;
import home.example.room_reserve_outer.data.dto.ReservationResponse;
import home.example.room_reserve_outer.data.entity.Reservation;
import home.example.room_reserve_outer.data.entity.Room;
import home.example.room_reserve_outer.data.type.Operation;
import home.example.room_reserve_outer.data.type.ReservationError;
import home.example.room_reserve_outer.data.type.ReservationResult;
import home.example.room_reserve_outer.data.type.Status;
import home.example.room_reserve_outer.repository.RecordRepository;
import home.example.room_reserve_outer.repository.ReservationRepository;
import home.example.room_reserve_outer.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "reservation.failure-rate=0",
        "spring.datasource.url=jdbc:h2:mem:outer_reservation_service_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@Transactional
class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

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
    void book_nullRequest_returnsParamFailureWithoutPersistence() {
        ReservationResponse response = reservationService.book(null);

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.PARAM);
        assertThat(recordRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void book_missingIdempotencyKey_returnsParamFailureWithoutRecord() {
        ReservationResponse response = reservationService.book(createRequest(
                null, "101", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.PARAM);
        assertThat(recordRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void book_invalidDate_recordsFailure() {
        ReservationResponse response = reservationService.book(createRequest(
                "invalid-date-key", "101", "guest", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 1),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.CHECK_DATE);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void book_invalidOperation_recordsFailure() {
        ReservationResponse response = reservationService.book(createRequest(
                "invalid-operation-key", "101", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CANCEL_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.OPERATION);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void book_unknownRoom_recordsRoomNotAvailableFailure() {
        ReservationResponse response = reservationService.book(createRequest(
                "unknown-room-key", "999", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.ROOM_NOT_AVAILABLE);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void book_maintenanceRoom_recordsRoomNotAvailableFailure() {
        ReservationResponse response = reservationService.book(createRequest(
                "maintenance-room-key", "202", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.ROOM_NOT_AVAILABLE);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void book_availableRoom_createsConfirmedReservationAndRecord() {
        ReservationRequest request = createRequest(
                "success-key", "101", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode());

        ReservationResponse response = reservationService.book(request);

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(response.getError_msg()).isNull();
        assertThat(response.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(response.getReservation_id()).isNotNull();
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(1L);

        Reservation reservation = reservationRepository.findById(response.getReservation_id()).orElseThrow(AssertionError::new);
        assertThat(reservation.getStatus()).isEqualTo(Status.CONFIRMED.getCode());
        assertThat(reservation.getCreateIdempotencyKey()).isEqualTo("success-key");
        assertThat(reservation.getGuestName()).isEqualTo("guest");
    }

    @Test
    void book_sameIdempotencyKeyAndSameRequest_returnsStoredResponse() {
        ReservationRequest request = createRequest(
                "same-create-key", "101", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode());

        ReservationResponse first = reservationService.book(request);
        ReservationResponse second = reservationService.book(request);

        assertThat(second.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(second.getReservation_id()).isEqualTo(first.getReservation_id());
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    @Test
    void book_sameIdempotencyKeyAndDifferentRequest_returnsConflictWithoutNewReservation() {
        reservationService.book(createRequest(
                "conflict-create-key", "101", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode()));

        ReservationResponse response = reservationService.book(createRequest(
                "conflict-create-key", "102", "guest", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.IDEMPOTENCY_CONFLICT);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    @Test
    void book_overlappingConfirmedReservation_recordsRoomAlreadyReservedFailure() {
        saveReservation("101", Status.CONFIRMED.getCode(), "existing-create-key",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));

        ReservationResponse response = reservationService.book(createRequest(
                "overlap-key", "101", "guest", LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 13),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.ROOM_ALREADY_RESERVED);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    @Test
    void book_adjacentConfirmedReservation_allowsNewReservation() {
        saveReservation("101", Status.CONFIRMED.getCode(), "existing-create-key",
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));

        ReservationResponse response = reservationService.book(createRequest(
                "adjacent-key", "101", "guest", LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 14),
                Operation.CREATE_RESERVATION.getCode()));

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(recordRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(2L);
    }

    @Test
    void checkBook_unknownReservation_returnsNotFoundFailure() {
        ReservationResponse response = reservationService.checkBook(999L);

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.RESERVATION_NOT_FOUND);
        assertThat(response.getReservation_id()).isEqualTo(999L);
    }

    @Test
    void checkBook_confirmedReservation_returnsSuccessWithStatus() {
        Reservation reservation = saveReservation("101", Status.CONFIRMED.getCode(), "check-confirmed-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse response = reservationService.checkBook(reservation.getId());

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(response.getRoom_number()).isEqualTo("101");
    }

    @Test
    void checkBook_cancelledReservation_returnsSuccessWithCancelledStatus() {
        Reservation reservation = saveReservation("101", Status.CANCELLED.getCode(), "check-cancelled-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse response = reservationService.checkBook(reservation.getId());

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(response.getError_msg()).isNull();
    }

    @Test
    void cancelBook_missingKeys_returnsParamFailureWithoutRecord() {
        ReservationResponse response = reservationService.cancelBook(1L, null, "create-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.PARAM);
        assertThat(recordRepository.count()).isZero();
    }

    @Test
    void cancelBook_unknownReservation_recordsNotFoundFailure() {
        ReservationResponse response = reservationService.cancelBook(999L, "cancel-not-found-key", "create-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.RESERVATION_NOT_FOUND);
        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    @Test
    void cancelBook_createKeyMismatch_recordsFailure() {
        Reservation reservation = saveReservation("101", Status.CONFIRMED.getCode(), "right-create-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse response = reservationService.cancelBook(reservation.getId(), "cancel-mismatch-key", "wrong-create-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.RESERVATION_CREATE_KEY_MISMATCH);
        assertThat(response.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    @Test
    void cancelBook_alreadyCancelledReservation_recordsFailure() {
        Reservation reservation = saveReservation("101", Status.CANCELLED.getCode(), "cancelled-create-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse response = reservationService.cancelBook(reservation.getId(), "cancel-already-key", "cancelled-create-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.RESERVATION_ALREADY_CANCELLED);
        assertThat(response.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    @Test
    void cancelBook_notConfirmedReservation_recordsFailure() {
        Reservation reservation = saveReservation("101", "pending", "pending-create-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse response = reservationService.cancelBook(reservation.getId(), "cancel-pending-key", "pending-create-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.RESERVATION_NOT_CONFIRMED);
        assertThat(response.getStatus()).isNull();
        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    @Test
    void cancelBook_confirmedReservation_cancelsAndStoresRecord() {
        Reservation reservation = saveReservation("101", Status.CONFIRMED.getCode(), "create-to-cancel-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse response = reservationService.cancelBook(reservation.getId(), "cancel-success-key", "create-to-cancel-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(response.getError_msg()).isNull();
        assertThat(recordRepository.count()).isEqualTo(1L);

        Reservation cancelledReservation = reservationRepository.findById(reservation.getId()).orElseThrow(AssertionError::new);
        assertThat(cancelledReservation.getStatus()).isEqualTo(Status.CANCELLED.getCode());
        assertThat(cancelledReservation.getCancelIdempotencyKey()).isEqualTo("cancel-success-key");
        assertThat(cancelledReservation.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelBook_sameIdempotencyKeyAndSameRequest_returnsStoredResponse() {
        Reservation reservation = saveReservation("101", Status.CONFIRMED.getCode(), "create-repeat-cancel-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));

        ReservationResponse first = reservationService.cancelBook(reservation.getId(), "cancel-repeat-key", "create-repeat-cancel-key");
        ReservationResponse second = reservationService.cancelBook(reservation.getId(), "cancel-repeat-key", "create-repeat-cancel-key");

        assertThat(second.getIs_success()).isEqualTo(ReservationResult.SUCCESS);
        assertThat(second.getReservation_id()).isEqualTo(first.getReservation_id());
        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    @Test
    void cancelBook_sameIdempotencyKeyAndDifferentRequest_returnsConflict() {
        Reservation firstReservation = saveReservation("101", Status.CONFIRMED.getCode(), "first-create-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        Reservation secondReservation = saveReservation("102", Status.CONFIRMED.getCode(), "second-create-key",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
        reservationService.cancelBook(firstReservation.getId(), "cancel-conflict-key", "first-create-key");

        ReservationResponse response = reservationService.cancelBook(secondReservation.getId(), "cancel-conflict-key", "second-create-key");

        assertThat(response.getIs_success()).isEqualTo(ReservationResult.FAILED);
        assertThat(response.getError_msg()).isEqualTo(ReservationError.IDEMPOTENCY_CONFLICT);
        assertThat(recordRepository.count()).isEqualTo(1L);
    }

    private ReservationRequest createRequest(String idempotencyKey,
                                             String roomNumber,
                                             String guestName,
                                             LocalDate checkIn,
                                             LocalDate checkOut,
                                             String operation) {
        ReservationRequest request = new ReservationRequest();
        request.setIdempotency_key(idempotencyKey);
        request.setRoom_number(roomNumber);
        request.setGuest_name(guestName);
        request.setCheckIn(checkIn);
        request.setCheckOut(checkOut);
        request.setOperation(operation);
        return request;
    }

    private Reservation saveReservation(String roomNumber,
                                        String status,
                                        String createIdempotencyKey,
                                        LocalDate checkIn,
                                        LocalDate checkOut) {
        Room room = roomRepository.findAll().stream()
                .filter(candidate -> roomNumber.equals(candidate.getRoomNumber()))
                .findFirst()
                .orElseThrow(AssertionError::new);

        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = new Reservation();
        reservation.setRoomId(room.getId());
        reservation.setGuestName("saved-guest");
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkOut);
        reservation.setStatus(status);
        reservation.setCreateIdempotencyKey(createIdempotencyKey);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        if(Status.CANCELLED.getCode().equals(status)) {
            reservation.setCancelledAt(now);
        }
        return reservationRepository.save(reservation);
    }
}
