package io.github.diegofranciscog.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import com.jayway.jsonpath.JsonPath;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.support.AbstractIntegrationTest;
import io.github.diegofranciscog.inventory.support.TestData;

/** API de punta a punta: autenticación, autorización por rol y por bodega, validaciones y cabeceras de seguridad. */
class ApiIT extends AbstractIntegrationTest {

    private static final String GTIN = "9527001000019";

    @Autowired
    private MockMvc mvc;

    private Warehouse quito;
    private Warehouse guayaquil;
    private Location quitoA;
    private Location guayaquilA;
    private Product soap;

    @BeforeEach
    void setUp() {
        quito = data.warehouse("BOD-UIO");
        guayaquil = data.warehouse("BOD-GYE");
        quitoA = data.location(quito, "01", "01", "1");
        guayaquilA = data.location(guayaquil, "01", "01", "1");
        soap = data.product("SOAP-1", GTIN, false);
        data.user("supervisor@test.local", Role.SUPERVISOR);
        data.user("operador@test.local", Role.OPERATOR, quito);
        data.user("auditor@test.local", Role.AUDITOR);
        data.user("admin@test.local", Role.ADMIN);
    }

    @Test
    void loginReturnsAShortLivedJwtAndHidesWhetherTheEmailExists() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("SUPERVISOR@test.local", TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("SUPERVISOR"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("supervisor@test.local", "incorrecta-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nadie@test.local", "incorrecta-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));

        Integer failures = jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN_FAILED'", Integer.class);
        assertThat(failures).isEqualTo(2);
    }

    @Test
    void loginIsRateLimited() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(credentials("supervisor@test.local", "incorrecta-" + attempt)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("supervisor@test.local", TestData.PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void everythingIsDeniedByDefaultWithoutAToken() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kardex").param("productId", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void rolesLimitWhatEachUserCanDo() throws Exception {
        String operator = token("operador@test.local");
        String auditor = token("auditor@test.local");

        mvc.perform(authorized(post("/api/movements/adjustments"), operator).content(adjustment()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(authorized(post("/api/movements/receipts"), auditor).content(receipt(quito, quitoA)))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(get("/api/audit-log"), operator, false)).andExpect(status().isForbidden());
        mvc.perform(authorized(get("/api/audit-log"), auditor, false)).andExpect(status().isOk());
        mvc.perform(authorized(get("/api/users"), token("supervisor@test.local"), false)).andExpect(status().isForbidden());
        mvc.perform(authorized(get("/api/users"), token("admin@test.local"), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem("admin@test.local")))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void anOperatorCannotTouchAWarehouseThatIsNotAssigned() throws Exception {
        String operator = token("operador@test.local");

        mvc.perform(authorized(post("/api/movements/receipts"), operator).content(receipt(quito, quitoA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number", containsString("ING-")));
        mvc.perform(authorized(post("/api/movements/receipts"), operator).content(receipt(guayaquil, guayaquilA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_FORBIDDEN"));
    }

    @Test
    void invalidInputGetsA400WithFieldMessagesAndNoInternals() throws Exception {
        String supervisor = token("supervisor@test.local");

        mvc.perform(authorized(post("/api/products"), supervisor).content("""
                        {"sku":"NEW-1","gtin":"9527001000018","name":"Nuevo","categoryId":1,"baseUomCode":"H87"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("gtin"));
        mvc.perform(authorized(post("/api/movements/issues"), supervisor).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Solicitud inválida: revisa el formato de los datos"));
        mvc.perform(authorized(post("/api/movements/issues"), supervisor).content("""
                        {"warehouseId":%d,"referenceTypeCode":"PED","referenceNumber":"P-1",
                         "lines":[{"productId":%d,"quantity":999}]}""".formatted(quito.getId(), soap.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void kardexExportsToExcelAndReconciles() throws Exception {
        String supervisor = token("supervisor@test.local");
        mvc.perform(authorized(post("/api/movements/receipts"), supervisor).content(receipt(quito, quitoA)))
                .andExpect(status().isCreated());

        MvcResult export = mvc.perform(authorized(get("/api/kardex/export"), supervisor, false)
                        .param("productId", soap.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, KardexController.XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("kardex-producto-")))
                .andReturn();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheet("Kardex").getRow(7).getCell(7).getNumericCellValue()).isEqualTo(12.0);
        }

        mvc.perform(authorized(get("/api/kardex"), supervisor, false).param("productId", soap.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costMethod", containsString("NIC 2")))
                .andExpect(jsonPath("$.entries.totalElements").value(1));
        mvc.perform(authorized(get("/api/reports/reconciliation"), supervisor, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
        mvc.perform(authorized(get("/api/reports/dashboard"), supervisor, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kardexBalanced").value(true))
                .andExpect(jsonPath("$.products").value(1));
    }

    @Test
    void scannerResolvesGtinGs1DigitalLinkAndLocationQr() throws Exception {
        String operator = token("operador@test.local");

        mvc.perform(authorized(post("/api/scan"), operator).content("{\"code\":\"" + GTIN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PRODUCT"))
                .andExpect(jsonPath("$.format").value("GTIN"))
                .andExpect(jsonPath("$.product.sku").value("SOAP-1"));
        mvc.perform(authorized(post("/api/scan"), operator)
                        .content("{\"code\":\"https://id.example.com/01/0" + GTIN + "/10/L7?17=271231\"}"))
                .andExpect(jsonPath("$.format").value("GS1_DIGITAL_LINK"))
                .andExpect(jsonPath("$.lotNumber").value("L7"))
                .andExpect(jsonPath("$.expiryDate").value("2027-12-31"));
        mvc.perform(authorized(post("/api/scan"), operator).content("{\"code\":\"LOC:BOD-UIO:P01-E01-N1\"}"))
                .andExpect(jsonPath("$.type").value("LOCATION"))
                .andExpect(jsonPath("$.location.code").value("P01-E01-N1"));
        mvc.perform(authorized(post("/api/scan"), operator).content("{\"code\":\"soap-1\"}"))
                .andExpect(jsonPath("$.format").value("SKU"));
        mvc.perform(authorized(post("/api/scan"), operator).content("{\"code\":\"no-existe\"}"))
                .andExpect(jsonPath("$.type").value("UNKNOWN"));
    }

    @Test
    void securityHeadersAndCorsAreStrict() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        mvc.perform(options("/api/products")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
        mvc.perform(options("/api/products")
                        .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private String token(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return authorized(request, token, true);
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token,
                                                            boolean json) {
        MockHttpServletRequestBuilder builder = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return json ? builder.contentType(MediaType.APPLICATION_JSON) : builder;
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String receipt(Warehouse warehouse, Location location) {
        return """
                {"warehouseId":%d,"referenceTypeCode":"01","referenceNumber":"001-001-000000001",
                 "lines":[{"productId":%d,"locationId":%d,"quantity":12,"unitCost":1.25}]}"""
                .formatted(warehouse.getId(), soap.getId(), location.getId());
    }

    private String adjustment() {
        return """
                {"warehouseId":%d,"referenceTypeCode":"AJ","referenceNumber":"AJ-1","reason":"prueba",
                 "lines":[{"productId":%d,"locationId":%d,"quantityDelta":1,"unitCost":1}]}"""
                .formatted(quito.getId(), soap.getId(), quitoA.getId());
    }
}
