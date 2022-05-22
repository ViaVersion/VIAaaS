package com.viaversion.aas.web

import com.google.common.net.HostAndPort

data class AddressInfo(
    val backVersion: Int,
    val backHostAndPort: HostAndPort,
    var frontOnline: Boolean? = null,
    var backName: String? = null
)