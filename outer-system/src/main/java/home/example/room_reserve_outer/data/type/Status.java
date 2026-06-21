package home.example.room_reserve_outer.data.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Status {
    CONFIRMED("confirmed", "예약"),
    CANCELLED("cancelled","취소");

    @JsonValue
    private final String code;
    private final String description;

    public static Status fromCode(String code) {
        if(code == null || code.trim().isEmpty() || "null".equalsIgnoreCase(code)) {
            return null;
        }

        for(Status status : values()) {
            if(status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }

        return null;
    }
}
