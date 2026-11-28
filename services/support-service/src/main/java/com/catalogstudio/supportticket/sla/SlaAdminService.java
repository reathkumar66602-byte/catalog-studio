package com.catalogstudio.supportticket.sla;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supportticket.dto.SlaConfigRequest;
import com.catalogstudio.supportticket.dto.SlaConfigResponse;
import com.catalogstudio.supportticket.entity.SupportSlaConfiguration;
import com.catalogstudio.supportticket.entity.SupportSlaHoliday;
import com.catalogstudio.supportticket.repository.SupportSlaConfigurationRepository;
import com.catalogstudio.supportticket.repository.SupportSlaHolidayRepository;
import com.catalogstudio.supportticket.service.SupportMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlaAdminService {

    private final SupportSlaConfigurationRepository configurationRepository;
    private final SupportSlaHolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public SlaConfigResponse getActive() {
        SupportSlaConfiguration config = configurationRepository.findFirstByActiveTrueOrderByIdAsc()
                .or(() -> configurationRepository.findAllByOrderByIdAsc().stream().findFirst())
                .orElseThrow(() -> ApiException.notFound("SLA configuration not found"));
        return SupportMapper.toSla(
                config, holidayRepository.findBySlaConfigIdOrderByHolidayDateAsc(config.getId()));
    }

    @Transactional
    public SlaConfigResponse updateActive(SlaConfigRequest request) {
        SupportSlaConfiguration config = configurationRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(() -> SupportSlaConfiguration.builder().active(true).build());
        config.setName(request.name().trim());
        config.setTimezone(request.timezone().trim());
        config.setWorkingDays(request.workingDays().trim());
        config.setWorkStart(request.workStart());
        config.setWorkEnd(request.workEnd());
        config.setInitialResponseBusinessDays(request.initialResponseBusinessDays());
        config.setWarnHoursBefore(request.warnHoursBefore());
        config.setActive(request.active() == null || request.active());
        config = configurationRepository.save(config);

        holidayRepository.deleteBySlaConfigId(config.getId());
        List<SupportSlaHoliday> holidays = new ArrayList<>();
        if (request.holidays() != null) {
            for (SlaConfigRequest.Holiday holiday : request.holidays()) {
                holidays.add(SupportSlaHoliday.builder()
                        .slaConfigId(config.getId())
                        .holidayDate(holiday.holidayDate())
                        .name(holiday.name().trim())
                        .build());
            }
            holidays = holidayRepository.saveAll(holidays);
        }
        return SupportMapper.toSla(config, holidays);
    }
}
