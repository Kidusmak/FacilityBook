package com.groupthree.Facilitybook.repository.projection;

public interface ResourceUsageProjection {

    Long getResourceId();

    String getResourceName();

    Long getReservationCount();

    Double getBookedHours();
}
