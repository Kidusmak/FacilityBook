package com.groupthree.Facilitybook;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring Security filter chain, method security and record-level checks
 * over HTTP. Service unit tests cover the rules; this test proves the rules are reachable
 * only with the right JWT and role, and that the JSON error contract is stable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.reminders.enabled=false")
@AutoConfigureMockMvc
class SecurityAuthorizationTest {

    private static final String ADMIN = "admin@facilitybook.local";
    private static final String EMPLOYEE = "employee@facilitybook.local";
    private static final String FACILITY_MANAGER = "facility@facilitybook.local";
    private static final String DEPARTMENT_MANAGER = "deptmanager@facilitybook.local";
    private static final String PASSWORD = "Password123!";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestGetsConsistentJson401() throws Exception {
        mockMvc.perform(get("/api/reservations/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/reservations/mine"));
    }

    @Test
    void employeeIsForbiddenFromStaffOnlyEndpoints() throws Exception {
        String token = login(EMPLOYEE);

        mockMvc.perform(post("/api/buildings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Employee Building\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reports/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCanReportDamageButCannotScheduleMaintenance() throws Exception {
        String token = login(EMPLOYEE);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maintenanceBody(1L, "Projector is broken", start, end, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REPORTED"));

        mockMvc.perform(post("/api/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maintenanceBody(1L, "Block the room", start, end, "SCHEDULED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void facilityManagerCanReadReportsButOnlyAdminManagesUsers() throws Exception {
        String facilityManager = login(FACILITY_MANAGER);
        mockMvc.perform(get("/api/reports/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(facilityManager)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(facilityManager)))
                .andExpect(status().isForbidden());

        String admin = login(ADMIN);
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateResourceAndEmployeeCannotResolveConflicts() throws Exception {
        String admin = login(ADMIN);
        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Security Test Room","categoryId":1,"capacity":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Security Test Room"));

        String employee = login(EMPLOYEE);
        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Employee Room","categoryId":1}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/reservations/1/resolve-conflict")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CANCEL_RESERVATION","note":"not allowed"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordLevelAuthorizationHidesReservationsFromOtherEmployees() throws Exception {
        String admin = login(ADMIN);
        long resourceId = createResource(admin, "Record Level Room");

        String otherEmail = "other.employee." + System.nanoTime() + "@facilitybook.local";
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!","fullName":"Other Employee","roleId":2,"departmentId":1,"active":true}
                                """.formatted(otherEmail)))
                .andExpect(status().isCreated());

        String owner = login(EMPLOYEE);
        Instant start = Instant.now().plus(11, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        MvcResult created = mockMvc.perform(post("/api/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"purpose":"Record level check","startTime":"%s","endTime":"%s"}
                                """.formatted(resourceId, start, end)))
                .andExpect(status().isCreated())
                .andReturn();
        long reservationId = ((Number) JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("Record level check"));

        String other = login(otherEmail);
        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());

        String departmentManager = login(DEPARTMENT_MANAGER);
        mockMvc.perform(get("/api/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(departmentManager)))
                .andExpect(status().isOk());
    }

    private long createResource(String adminToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","categoryId":1}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String maintenanceBody(Long resourceId, String reason, Instant start, Instant end,
                                   String status) {
        String statusField = status != null ? ",\"status\":\"" + status + "\"" : "";
        return """
                {"resourceId":%d,"reason":"%s","startTime":"%s","endTime":"%s"%s}
                """.formatted(resourceId, reason, start, end, statusField);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
