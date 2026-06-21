package home.example.room_reserve_outer.data.type;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationResult {
    SUCCESS("success", "성공"),
    FAILED("failed", "실패");

    @JsonValue
    private final String code;
    private final String description;

    public static ReservationResult fromCode(String code) {
        if(code == null || code.trim().isEmpty() || "null".equalsIgnoreCase(code)) {
            return null;
        }

        for(ReservationResult result : values()) {
            if(result.code.equalsIgnoreCase(code)) {
                return result;
            }
        }

        return null;
    }
}
