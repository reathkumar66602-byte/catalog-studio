package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportSlaHoliday;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportSlaHolidayRepository extends JpaRepository<SupportSlaHoliday, Long> {

    List<SupportSlaHoliday> findBySlaConfigIdOrderByHolidayDateAsc(Long slaConfigId);
    void deleteBySlaConfigId(Long slaConfigId);

}
