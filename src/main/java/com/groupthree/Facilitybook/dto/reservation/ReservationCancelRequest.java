package com.groupthree.Facilitybook.dto.reservation;

import jakarta.validation.constraints.Size;

public record ReservationCancelRequest(

        @Size(max = 300, message = "Cancellation reason must be at most 300 characters")
        String reason) {
}
