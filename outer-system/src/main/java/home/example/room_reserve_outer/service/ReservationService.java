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
import home.example.room_reserve_outer.util.HashUtil;
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
        if(reservationRequest == null) {
            return buildFailedResponse(null, ReservationError.PARAM);
        }

        // 동일한 idempotency key로 처리된 요청이 있으면 저장된 응답을 반환한다.
        ReservationResponse idempotentResponse = findExistingIdempotentResponse(reservationRequest);
        if(idempotentResponse != null) {
            return idempotentResponse;
        }

        // 필수값, 날짜, 생성 operation 여부를 검증하고 실패 응답을 기록한다.
        ReservationResponse validationFailure = validateCreateRequest(reservationRequest);
        if(validationFailure != null) {
            if(isBlank(reservationRequest.getIdempotency_key())) {
                return validationFailure;
            }
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
        // reservation id로 예약 정보를 조회한다.
        Optional<Reservation> optionalReservation = reservationRepository.findById(reservationId);
        if(!optionalReservation.isPresent()) {
            return buildCheckFailedResponse(reservationId, ReservationError.RESERVATION_NOT_FOUND);
        }

        Reservation reservation = optionalReservation.get();
        return buildCheckSuccessResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancelBook(long reservationId, String idempotencyKey, String createIdempotencyKey) {
        // 취소 요청에도 idempotency key를 필수로 요구한다.
        if(isBlank(idempotencyKey) || isBlank(createIdempotencyKey)) {
            return buildCancelFailedResponse(reservationId, ReservationError.PARAM);
        }

        // 동일한 취소 요청이 이미 처리되었다면 저장된 응답을 반환한다.
        String requestHash = buildCancelRequestHash(reservationId, createIdempotencyKey);
        ReservationResponse idempotentResponse = findExistingCancelIdempotentResponse(idempotencyKey, requestHash);
        if(idempotentResponse != null) {
            return idempotentResponse;
        }

        // 취소 대상 예약이 존재하는지 확인한다.
        Optional<Reservation> optionalReservation = reservationRepository.findById(reservationId);
        if(!optionalReservation.isPresent()) {
            return rejectCancelAndRecord(idempotencyKey, requestHash,
                    buildCancelFailedResponse(reservationId, idempotencyKey, ReservationError.RESERVATION_NOT_FOUND));
        }

        Reservation reservation = optionalReservation.get();
        // 예약 생성 시 사용한 idempotency key가 일치해야 취소할 수 있다.
        if(!createIdempotencyKey.equals(reservation.getCreateIdempotencyKey())) {
            return rejectCancelAndRecord(idempotencyKey, requestHash,
                    buildCancelFailedResponse(reservation, idempotencyKey, ReservationError.RESERVATION_CREATE_KEY_MISMATCH));
        }

        // 이미 취소된 예약은 다시 취소하지 않고 실패 응답을 기록한다.
        if(Status.CANCELLED.getCode().equalsIgnoreCase(reservation.getStatus())) {
            return rejectCancelAndRecord(idempotencyKey, requestHash,
                    buildCancelFailedResponse(reservation, idempotencyKey, ReservationError.RESERVATION_ALREADY_CANCELLED));
        }

        // 확정 상태가 아닌 예약은 취소할 수 없다.
        if(!Status.CONFIRMED.getCode().equalsIgnoreCase(reservation.getStatus())) {
            return rejectCancelAndRecord(idempotencyKey, requestHash,
                    buildCancelFailedResponse(reservation, idempotencyKey, ReservationError.RESERVATION_NOT_CONFIRMED));
        }

        // 예약을 취소하고 성공 응답을 idempotency record에 저장한다.
        cancelReservation(reservation, idempotencyKey);
        ReservationResponse response = buildCancelSuccessResponse(reservation, idempotencyKey);
        saveRecord(idempotencyKey, Operation.CANCEL_RESERVATION, requestHash, response, reservation.getId());
        return response;
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
        if(request == null || isBlank(request.getIdempotency_key())) {
            return null;
        }

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

    private ReservationResponse findExistingCancelIdempotentResponse(String idempotencyKey, String requestHash) {
        Optional<IdempotencyRecord> optionalRecord = recordRepository.findByIdempotencyKey(idempotencyKey);
        if(optionalRecord.isPresent()) {
            IdempotencyRecord record = optionalRecord.get();
            if(record.getRequestHash() != null && !record.getRequestHash().equals(requestHash)) {
                return buildCancelFailedResponse((Long) null, idempotencyKey, ReservationError.IDEMPOTENCY_CONFLICT);
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
                .idempotency_key(request == null ? null : request.getIdempotency_key())
                .room_number(request == null ? null : request.getRoom_number())
                .operation(request == null ? null : request.getOperation())
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
                .status(Status.CONFIRMED)
                .is_success(ReservationResult.SUCCESS)
                .error_msg(null)
                .build();
    }

    private ReservationResponse buildCheckSuccessResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .reservation_id(reservation.getId())
                .room_number(findRoomNumber(reservation.getRoomId()))
                .operation(Operation.CHECK_RESERVATION.getCode())
                .status(Status.fromCode(reservation.getStatus()))
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
                .status(Status.fromCode(reservation.getStatus()))
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

    private void cancelReservation(Reservation reservation, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        reservation.setStatus(Status.CANCELLED.getCode());
        reservation.setCancelIdempotencyKey(idempotencyKey);
        reservation.setCancelledAt(now);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);
    }

    private ReservationResponse buildCancelSuccessResponse(Reservation reservation, String idempotencyKey) {
        return ReservationResponse.builder()
                .idempotency_key(idempotencyKey)
                .reservation_id(reservation.getId())
                .room_number(findRoomNumber(reservation.getRoomId()))
                .operation(Operation.CANCEL_RESERVATION.getCode())
                .status(Status.CANCELLED)
                .is_success(ReservationResult.SUCCESS)
                .error_msg(null)
                .build();
    }

    private ReservationResponse buildCancelFailedResponse(Long reservationId, String idempotencyKey, ReservationError error) {
        return ReservationResponse.builder()
                .idempotency_key(idempotencyKey)
                .reservation_id(reservationId)
                .operation(Operation.CANCEL_RESERVATION.getCode())
                .is_success(ReservationResult.FAILED)
                .error_msg(error)
                .build();
    }

    private ReservationResponse buildCancelFailedResponse(long reservationId, ReservationError error) {
        return buildCancelFailedResponse(reservationId, null, error);
    }

    private ReservationResponse buildCancelFailedResponse(Reservation reservation, String idempotencyKey, ReservationError error) {
        return ReservationResponse.builder()
                .idempotency_key(idempotencyKey)
                .reservation_id(reservation.getId())
                .room_number(findRoomNumber(reservation.getRoomId()))
                .operation(Operation.CANCEL_RESERVATION.getCode())
                .status(Status.fromCode(reservation.getStatus()))
                .is_success(ReservationResult.FAILED)
                .error_msg(error)
                .build();
    }

    private void saveRecord(ReservationRequest request, ReservationResponse response) {
        saveRecord(request, response, response.getReservation_id());
    }

    private void saveRecord(ReservationRequest request, ReservationResponse response, Long resourceId) {
        saveRecord(request.getIdempotency_key(), Operation.CREATE_RESERVATION, request.hash(), response, resourceId);
    }

    private void saveRecord(String idempotencyKey,
                            Operation operation,
                            String requestHash,
                            ReservationResponse response,
                            Long resourceId) {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setOperationType(operation.getCode());
        record.setResourceType(RESOURCE_TYPE_RESERVATION);
        if(resourceId != null) {
            record.setResourceId(resourceId);
        }
        record.setRequestHash(requestHash);
        record.setResponsePayload(response.toPayload());
        record.setHttpStatus(resolveHttpStatus(response));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        recordRepository.save(record);
    }

    private ReservationResponse rejectCancelAndRecord(String idempotencyKey, String requestHash, ReservationResponse response) {
        saveRecord(idempotencyKey, Operation.CANCEL_RESERVATION, requestHash, response, response.getReservation_id());
        return response;
    }

    private String buildCancelRequestHash(long reservationId, String createIdempotencyKey) {
        String input = String.join("|",
                Operation.CANCEL_RESERVATION.getCode(),
                String.valueOf(reservationId),
                String.valueOf(createIdempotencyKey));
        return HashUtil.hash(input);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
