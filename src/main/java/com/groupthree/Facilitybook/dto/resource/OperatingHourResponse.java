package com.groupthree.Facilitybook.dto.resource;

import com.groupthree.Facilitybook.entity.OperatingHour;

import java.time.LocalTime;

public record OperatingHourResponse(
        Long id,
        Short dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime) {

    public static OperatingHourResponse from(OperatingHour hour) {
        return new OperatingHourResponse(
                hour.getId(),
                hour.getDayOfWeek(),
                hour.getOpenTime(),
                hour.getCloseTime());
    }
}
