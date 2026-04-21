package dev.kensa.plugin.intellij.execution

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

@Service(PROJECT)
class KensaRunTabRegistry(private val project: Project) {

    private val map = ConcurrentHashMap<RunContentDescriptor, String>()

    fun tag(descriptor: RunContentDescriptor, indexHtmlPath: String) {
        if (Disposer.isDisposed(descriptor)) return
        if (map.putIfAbsent(descriptor, indexHtmlPath) == null) {
            registerCleanup(descriptor)
        }
    }

    fun indexPathFor(descriptor: RunContentDescriptor): String? = map[descriptor]

    private fun registerCleanup(descriptor: RunContentDescriptor) {
        if (Disposer.isDisposed(descriptor)) {
            map.remove(descriptor)
            return
        }
        Disposer.register(descriptor) { map.remove(descriptor) }
    }
}
