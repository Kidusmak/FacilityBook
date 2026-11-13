package com.groupthree.Facilitybook.repository.projection;

public interface DepartmentUsageProjection {

    Long getDepartmentId();

    String getDepartmentName();

    Long getReservationCount();
}
