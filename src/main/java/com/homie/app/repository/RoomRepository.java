package com.homie.app.repository;

import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    // A house's rooms in bins-rota order, for the schedule page and the
    // room dropdown on the profile page.
    List<Room> findByHouseOrderByOrderIndexAsc(House house);

    long countByHouse(House house);

    // A plain bulk SQL delete, used only when a house's last owner
    // deletes their account (see HouseService.deleteHouse). Deliberately
    // bypasses Hibernate's normal entity-by-entity deletion - going
    // through the persistence context for this specific cleanup (deleting
    // a House whose createdBy still points at a User in the same flush)
    // triggers a Hibernate quirk where it tries to null out House's
    // required createdBy column before the delete, which the database
    // rejects. A native bulk delete just runs the SQL directly, with none
    // of that entity-association bookkeeping to trip over.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM rooms WHERE house_id = :houseId", nativeQuery = true)
    void deleteAllByHouseIdNative(@Param("houseId") Long houseId);
}
