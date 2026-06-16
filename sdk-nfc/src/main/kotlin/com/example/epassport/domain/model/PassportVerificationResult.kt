package com.example.epassport.domain.model

/**
 * パスポートの Passive Authentication / Active Authentication 検証結果。
 */
data class PassportVerificationResult(
    /** 全体として検証が成功したかどうか */
    val isSuccessful: Boolean,
    /** Passive Authentication の結果 */
    val passiveAuthentication: AuthenticationStepResult,
    /** Active Authentication の結果（未実施の場合は null） */
    val activeAuthentication: AuthenticationStepResult?,
    /** 失敗時の詳細メッセージ（成功時は null） */
    val failureReason: String? = null
) {
    companion object {
        fun success(
            passiveAuthentication: AuthenticationStepResult,
            activeAuthentication: AuthenticationStepResult?
        ): PassportVerificationResult {
            val overall = passiveAuthentication.success && (activeAuthentication?.success ?: true)
            return PassportVerificationResult(
                isSuccessful = overall,
                passiveAuthentication = passiveAuthentication,
                activeAuthentication = activeAuthentication,
                failureReason = if (!overall) buildFailureReason(passiveAuthentication, activeAuthentication) else null
            )
        }

        fun failure(
            passiveAuthentication: AuthenticationStepResult,
            activeAuthentication: AuthenticationStepResult?,
            reason: String
        ): PassportVerificationResult {
            return PassportVerificationResult(
                isSuccessful = false,
                passiveAuthentication = passiveAuthentication,
                activeAuthentication = activeAuthentication,
                failureReason = reason
            )
        }

        private fun buildFailureReason(
            passive: AuthenticationStepResult,
            active: AuthenticationStepResult?
        ): String {
            val parts = mutableListOf<String>()
            if (!passive.success) parts.add("PA failed: ${passive.detail}")
            if (active != null && !active.success) parts.add("AA failed: ${active.detail}")
            return parts.joinToString("; ")
        }
    }
}

/**
 * 個別の認証ステップ結果。
 */
data class AuthenticationStepResult(
    /** ステップが成功したかどうか */
    val success: Boolean,
    /** ステップ名 */
    val stepName: String,
    /** 結果詳細（成功/失敗理由） */
    val detail: String
) {
    companion object {
        fun success(stepName: String, detail: String = "OK"): AuthenticationStepResult {
            return AuthenticationStepResult(success = true, stepName = stepName, detail = detail)
        }

        fun failure(stepName: String, detail: String): AuthenticationStepResult {
            return AuthenticationStepResult(success = false, stepName = stepName, detail = detail)
        }
    }
}
