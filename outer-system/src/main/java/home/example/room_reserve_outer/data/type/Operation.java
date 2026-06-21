package home.example.room_reserve_outer.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Operation {
    CREATE_RESERVATION("create_reservation", "예약"),
    CHECK_RESERVATION("check_reservation", "예약 조회"),
    CANCEL_RESERVATION("cancel_reservation", "예약 취소");

    private final String code;
    private final String description;
}
