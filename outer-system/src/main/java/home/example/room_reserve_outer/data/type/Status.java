package home.example.room_reserve_outer.data.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Status {
    CONFIRMED("confirmed", "예약"),
    CANCELLED("cancelled","취소");

    private final String code;
    private final String description;
}
