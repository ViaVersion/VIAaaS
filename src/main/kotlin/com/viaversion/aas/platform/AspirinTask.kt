package com.viaversion.aas.platform

import us.myles.ViaVersion.api.platform.TaskId
import java.util.concurrent.Future

class AspirinTask(val obj: Future<*>) : TaskId {
    override fun getObject(): Any = obj
}