package home.example.room_reserve_outer.data.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationError {
    PARAM("param error", "필수 파라미터 누락"),
    CHECK_DATE("check date error", "체크인/체크아웃 날짜 오류"),
    OPERATION("operation error", "지원하지 않는 예약 작업"),
    ROOM_NOT_AVAILABLE("room is not available", "예약할 수 없는 객실"),
    ROOM_ALREADY_RESERVED("room is already reserved", "이미 예약된 객실"),
    IDEMPOTENCY_CONFLICT("idempotency key conflict", "멱등성 키 충돌");

    @JsonValue
    private final String code;
    private final String description;

    public static ReservationError fromCode(String code) {
        if(code == null || code.trim().isEmpty() || "null".equalsIgnoreCase(code)) {
            return null;
        }

        for(ReservationError error : values()) {
            if(error.code.equalsIgnoreCase(code)) {
                return error;
            }
        }

        return null;
    }
}
