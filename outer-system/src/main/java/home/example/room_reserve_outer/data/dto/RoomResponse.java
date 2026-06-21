package home.example.room_reserve_outer.data.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomResponse {
    private String room_number;
    private String room_type;
    private String status;
    private Boolean availability;
}
