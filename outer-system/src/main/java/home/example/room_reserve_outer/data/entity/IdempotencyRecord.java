package home.example.room_reserve_outer.data.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name="uk_idempotency_records_key",
                        columnNames = "idempotencyKey"
                )
        }
        )
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String idempotencyKey;
    private String operationType;
    private String resourceType;
    private long resourceId;
    private String requestHash;
    private String responsePayload;
    private int httpStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
