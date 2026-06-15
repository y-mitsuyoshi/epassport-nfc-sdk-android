package com.example.epassport.server.controller

import com.example.epassport.server.service.PassportVerificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * サーバー側でパスポートの PA/AA 検証を行うコントローラー。
 */
@RestController
@RequestMapping("/api/v1/verification")
class VerificationController(
    private val verificationService: PassportVerificationService
) {

    @PostMapping("/passport")
    fun verifyPassport(@RequestBody request: PassportVerificationService.VerificationRequest): ResponseEntity<VerificationResponse> {
        val result = verificationService.verify(request)
        return ResponseEntity.ok(
            VerificationResponse(
                successful = result.successful,
                paSuccess = result.paSuccess,
                aaSuccess = result.aaSuccess,
                failureReason = result.failureReason
            )
        )
    }

    data class VerificationResponse(
        val successful: Boolean,
        val paSuccess: Boolean,
        val aaSuccess: Boolean?,
        val failureReason: String?
    )
}
