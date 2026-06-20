package home.example.room_reserve_outer.data.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private long roomId;
    private String guestName;
    private LocalDateTime checkInDate;
    private LocalDateTime checkOutDate;
    private String status;
    private String createIdempotencyKey;
    private String cancelIdempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
