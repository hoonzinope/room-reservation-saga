package home.example.room_reserve_outer.service;

import home.example.room_reserve_outer.data.dto.ReservationRequest;
import home.example.room_reserve_outer.data.dto.ReservationResponse;
import home.example.room_reserve_outer.data.entity.IdempotencyRecord;
import home.example.room_reserve_outer.data.entity.Reservation;
import home.example.room_reserve_outer.data.entity.Room;
import home.example.room_reserve_outer.data.type.Operation;
import home.example.room_reserve_outer.data.type.ReservationError;
import home.example.room_reserve_outer.data.type.ReservationResult;
import home.example.room_reserve_outer.data.type.Status;
import home.example.room_reserve_outer.repository.RecordRepository;
import home.example.room_reserve_outer.repository.ReservationRepository;
import home.example.room_reserve_outer.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ReservationService {
    private static final String RESOURCE_TYPE_RESERVATION = "RESERVATION";

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final RecordRepository recordRepository;

    public ReservationService(RoomRepository roomRepository,
                              ReservationRepository reservationRepository,
                              RecordRepository recordRepository) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public ReservationResponse book(ReservationRequest reservationRequest) {
        // 동일한 idempotency key로 처리된 요청이 있으면 저장된 응답을 반환한다.
        ReservationResponse idempotentResponse = findExistingIdempotentResponse(reservationRequest);
        if(idempotentResponse != null) {
            return idempotentResponse;
        }

        // 필수값, 날짜, 생성 operation 여부를 검증하고 실패 응답을 기록한다.
        ReservationResponse validationFailure = validateCreateRequest(reservationRequest);
        if(validationFailure != null) {
            return rejectAndRecord(reservationRequest, validationFailure);
        }

        // 예약 가능한 객실인지 확인한다.
        Room room = findAvailableRoom(reservationRequest);
        if(room == null) {
            return rejectAndRecord(reservationRequest, ReservationError.ROOM_NOT_AVAILABLE);
        }

        // 요청 기간과 겹치는 확정 예약이 있는지 확인한다.
        ReservationResponse overlapFailure = validateNoOverlappingReservation(reservationRequest);
        if(overlapFailure != null) {
            return rejectAndRecord(reservationRequest, overlapFailure);
        }

        // 예약을 생성하고 성공 응답을 idempotency record에 저장한다.
        Reservation successReservation = saveReservation(room, reservationRequest);
        ReservationResponse successResponse = buildSuccessResponse(reservationRequest, successReservation);
        saveRecord(reservationRequest, successResponse, successReservation.getId());

        return successResponse;
    }

    @Transactional(readOnly = true)
    public ReservationResponse checkBook(long reservationId) {
        Optional<Reservation> optionalReservation = reservationRepository.findById(reservationId);
        if(!optionalReservation.isPresent()) {
            return buildCheckFailedResponse(reservationId, ReservationError.RESERVATION_NOT_FOUND);
        }

        Reservation reservation = optionalReservation.get();
        if(!Status.CONFIRMED.getCode().equalsIgnoreCase(reservation.getStatus())) {
            return buildCheckFailedResponse(reservation, ReservationError.RESERVATION_NOT_CONFIRMED);
        }

        return buildCheckSuccessResponse(reservation);
    }

    public void cancelBook() {
        // reservation_id로 예약 취소
    }

    private ReservationResponse validateCreateRequest(ReservationRequest request) {
        ReservationError error = null;
        if(!request.hasAllParam()){
            error = ReservationError.PARAM;
        } else if(!request.isValidCheckDate()) {
            error = ReservationError.CHECK_DATE;
        } else if(!request.isCreateReservationOperation()) {
            error = ReservationError.OPERATION;
        }

        if(error != null) {
            return buildFailedResponse(request, error);
        }
        return null;
    }

    private Room findAvailableRoom(ReservationRequest request) {
        Optional<Room> optionalRoom = roomRepository.findByRoomNumber(request.getRoom_number());
        if(!optionalRoom.isPresent()) {
            return null;
        }

        Room room = optionalRoom.get();
        if(!room.isAvailable()) {
            return null;
        }

        return room;
    }

    private ReservationResponse validateNoOverlappingReservation(ReservationRequest request) {
        boolean exists = reservationRepository.existsConfirmedReservationByRoomNumberAndDateRange(
                request.getRoom_number(),
                Status.CONFIRMED.getCode(),
                request.getCheckIn(),
                request.getCheckOut()
        );
        if(exists) {
            return buildFailedResponse(request, ReservationError.ROOM_ALREADY_RESERVED);
        }
        return null;
    }

    private ReservationResponse findExistingIdempotentResponse(ReservationRequest request) {
        Optional<IdempotencyRecord> optionalRecord = recordRepository.findByIdempotencyKey(request.getIdempotency_key());
        if(optionalRecord.isPresent()) {
            IdempotencyRecord record = optionalRecord.get();
            if(record.getRequestHash() != null && !record.getRequestHash().equals(request.hash())) {
                return buildFailedResponse(request, ReservationError.IDEMPOTENCY_CONFLICT);
            }
            ReservationResponse response = ReservationResponse.builder().build();
            return response.payloadToResponse(record.getResponsePayload());
        }
        return null;
    }

    private Reservation saveReservation(Room room, ReservationRequest request) {
        Reservation reservation = new Reservation();
        reservation.setRoomId(room.getId());
        reservation.setGuestName(request.getGuest_name());
        reservation.setCheckInDate(request.getCheckIn());
        reservation.setCheckOutDate(request.getCheckOut());
        reservation.setStatus(Status.CONFIRMED.getCode());
        reservation.setCreateIdempotencyKey(request.getIdempotency_key());
        reservation.setCreatedAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        return reservationRepository.save(reservation);
    }

    private ReservationResponse buildFailedResponse(ReservationRequest request, ReservationError error) {
        return ReservationResponse.builder()
                .idempotency_key(request.getIdempotency_key())
                .room_number(request.getRoom_number())
                .operation(request.getOperation())
                .is_success(ReservationResult.FAILED)
                .error_msg(error)
                .build();
    }

    private ReservationResponse rejectAndRecord(ReservationRequest request, ReservationError error) {
        ReservationResponse response = buildFailedResponse(request, error);
        saveRecord(request, response);
        return response;
    }

    private ReservationResponse rejectAndRecord(ReservationRequest request, ReservationResponse response) {
        if(response == null) {
            return null;
        }
        saveRecord(request, response);
        return response;
    }

    private ReservationResponse buildSuccessResponse(ReservationRequest request, Reservation reservation) {
        return ReservationResponse.builder()
                .idempotency_key(request.getIdempotency_key())
                .reservation_id(reservation.getId())
                .room_number(request.getRoom_number())
                .operation(request.getOperation())
                .is_success(ReservationResult.SUCCESS)
                .error_msg(null)
                .build();
    }

    private ReservationResponse buildCheckSuccessResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .reservation_id(reservation.getId())
                .room_number(findRoomNumber(reservation.getRoomId()))
                .operation(Operation.CHECK_RESERVATION.getCode())
                .is_success(ReservationResult.SUCCESS)
                .error_msg(null)
                .build();
    }

    private ReservationResponse buildCheckFailedResponse(long reservationId, ReservationError error) {
        return ReservationResponse.builder()
                .reservation_id(reservationId)
                .operation(Operation.CHECK_RESERVATION.getCode())
                .is_success(ReservationResult.FAILED)
                .error_msg(error)
                .build();
    }

    private ReservationResponse buildCheckFailedResponse(Reservation reservation, ReservationError error) {
        return ReservationResponse.builder()
                .reservation_id(reservation.getId())
                .room_number(findRoomNumber(reservation.getRoomId()))
                .operation(Operation.CHECK_RESERVATION.getCode())
                .is_success(ReservationResult.FAILED)
                .error_msg(error)
                .build();
    }

    private String findRoomNumber(long roomId) {
        Optional<Room> optionalRoom = roomRepository.findById(roomId);
        if(!optionalRoom.isPresent()) {
            return null;
        }

        return optionalRoom.get().getRoomNumber();
    }

    private void saveRecord(ReservationRequest request, ReservationResponse response) {
        saveRecord(request, response, response.getReservation_id());
    }

    private void saveRecord(ReservationRequest request, ReservationResponse response, Long resourceId) {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(request.getIdempotency_key());
        record.setOperationType(Operation.CREATE_RESERVATION.getCode());
        record.setResourceType(RESOURCE_TYPE_RESERVATION);
        if(resourceId != null) {
            record.setResourceId(resourceId);
        }
        record.setRequestHash(request.hash());
        record.setResponsePayload(response.toPayload());
        record.setHttpStatus(resolveHttpStatus(response));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        recordRepository.save(record);
    }

    private int resolveHttpStatus(ReservationResponse response) {
        if(response == null) {
            return 500;
        }

        if(response.getIs_success() == ReservationResult.SUCCESS) {
            return 200;
        }

        ReservationError error = response.getError_msg();
        if(error == null) {
            return 400;
        }

        if(error == ReservationError.IDEMPOTENCY_CONFLICT ||
                error == ReservationError.ROOM_ALREADY_RESERVED) {
            return 409;
        }

        return 400;
    }
}
