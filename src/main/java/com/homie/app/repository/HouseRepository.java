package com.homie.app.repository;

import com.homie.app.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HouseRepository extends JpaRepository<House, Long> {

    // Used both when someone tries to join with a code, and when
    // generating a brand new code, to make sure it isn't already taken.
    Optional<House> findByInviteCodeIgnoreCase(String inviteCode);

    boolean existsByInviteCodeIgnoreCase(String inviteCode);

    // A plain bulk SQL delete - see RoomRepository.deleteAllByHouseIdNative
    // for why this bypasses the normal entity-level delete(). Only used
    // when a house's last remaining member (who must be its owner)
    // deletes their account, so the now-empty house is removed too.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM houses WHERE id = :id", nativeQuery = true)
    void deleteByIdNative(@Param("id") Long id);
}
