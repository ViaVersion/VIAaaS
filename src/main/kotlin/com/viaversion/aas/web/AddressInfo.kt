package com.viaversion.aas.web

import com.google.common.net.HostAndPort
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion

data class AddressInfo(
    val backVersion: ProtocolVersion,
    val backHostAndPort: HostAndPort,
    var frontOnline: Boolean? = null,
    var backName: String? = null
)