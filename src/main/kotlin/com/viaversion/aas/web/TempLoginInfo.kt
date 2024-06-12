package com.viaversion.aas.web

import java.util.UUID

data class TempLoginInfo(val tempCode: String, val username: String, val id: UUID)
