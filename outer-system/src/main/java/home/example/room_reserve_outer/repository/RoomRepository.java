package home.example.room_reserve_outer.repository;

import home.example.room_reserve_outer.data.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Room> findByRoomNumber(String roomNumber);

    @Query("select room from Room room where room.roomNumber = :roomNumber")
    Optional<Room> findForAvailabilityCheck(@Param("roomNumber") String roomNumber);
}
