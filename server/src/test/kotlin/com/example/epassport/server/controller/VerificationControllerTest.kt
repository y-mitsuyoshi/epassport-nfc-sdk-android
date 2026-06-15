package com.example.epassport.server.controller

import com.example.epassport.server.service.PassportVerificationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class VerificationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var verificationService: PassportVerificationService

    @Test
    fun verifyPassport_returnsResult() {
        `when`(verificationService.verify(org.mockito.kotlin.any()))
            .thenReturn(PassportVerificationService.VerificationResult(true, true, null, null))

        val requestBody = """
            {
                "sodBase64": "dGVzdA==",
                "dataGroups": {"1": "dGVzdA=="}
            }
        """.trimIndent()

        mockMvc.post("/api/v1/verification/passport") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.successful") { value(true) }
        }
    }
}
