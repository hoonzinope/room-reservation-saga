package home.example.room_reserve_outer.repository;

import home.example.room_reserve_outer.data.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select case when count(r) > 0 then true else false end " +
            "from Reservation r " +
            "join Room room on r.roomId = room.id " +
            "where room.roomNumber = :roomNumber " +
            "and r.status = :status " +
            "and r.checkInDate < :checkOutDate " +
            "and r.checkOutDate > :checkInDate")
    boolean existsConfirmedReservationByRoomNumberAndDateRange(@Param("roomNumber") String roomNumber,
                                                               @Param("status") String status,
                                                               @Param("checkInDate") LocalDate checkInDate,
                                                               @Param("checkOutDate") LocalDate checkOutDate);

}
