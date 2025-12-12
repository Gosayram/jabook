package com.jabook.app.jabook.compose.domain.repository

import com.jabook.app.jabook.compose.domain.model.CaptchaData

class CaptchaRequiredException(
    val captchaData: CaptchaData,
) : Exception("Captcha required")
