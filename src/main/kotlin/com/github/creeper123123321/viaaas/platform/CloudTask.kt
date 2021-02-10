package com.github.creeper123123321.viaaas.platform

import us.myles.ViaVersion.api.platform.TaskId
import java.util.concurrent.Future

class CloudTask(val obj: Future<*>) : TaskId {
    override fun getObject(): Any = obj
}