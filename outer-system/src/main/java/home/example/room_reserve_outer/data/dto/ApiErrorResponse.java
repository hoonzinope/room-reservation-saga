package home.example.room_reserve_outer.data.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiErrorResponse {
    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}
