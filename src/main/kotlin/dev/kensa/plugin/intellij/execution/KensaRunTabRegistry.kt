package dev.kensa.plugin.intellij.execution

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import java.util.concurrent.ConcurrentHashMap

@Service(PROJECT)
class KensaRunTabRegistry(private val project: Project) {

    private val seenClasses = ConcurrentHashMap<RunContentDescriptor, MutableSet<String>>()

    fun recordClass(descriptor: RunContentDescriptor, classFqn: String) {
        if (Disposer.isDisposed(descriptor)) return
        val set = seenClasses.computeIfAbsent(descriptor) {
            registerCleanup(descriptor)
            ConcurrentHashMap.newKeySet()
        }
        set.add(classFqn)
    }

    fun indexPathFor(descriptor: RunContentDescriptor): String? {
        val classes = seenClasses[descriptor] ?: return null
        val service = project.service<KensaTestResultsService>()
        return classes.firstNotNullOfOrNull { service.getIndexPath(it) }
    }

    private fun registerCleanup(descriptor: RunContentDescriptor) {
        if (Disposer.isDisposed(descriptor)) {
            seenClasses.remove(descriptor)
            return
        }
        Disposer.register(descriptor) { seenClasses.remove(descriptor) }
    }
}
