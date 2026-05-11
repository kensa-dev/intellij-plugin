package dev.kensa.plugin.intellij.execution

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
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
        val newSet = ConcurrentHashMap.newKeySet<String>()
        val existing = seenClasses.putIfAbsent(descriptor, newSet)
        if (existing == null) {
            newSet.add(classFqn)
            registerCleanup(descriptor)
        } else {
            existing.add(classFqn)
        }
    }

    fun indexPathFor(descriptor: RunContentDescriptor): String? {
        val classes = seenClasses[descriptor] ?: return null
        val service = project.service<KensaTestResultsService>()
        return classes.firstNotNullOfOrNull { service.getIndexPath(it) }
    }

    fun firstClassWithReport(descriptor: RunContentDescriptor): String? {
        val classes = seenClasses[descriptor] ?: return null
        val service = project.service<KensaTestResultsService>()
        return classes.firstOrNull { service.getIndexPath(it) != null }
    }

    fun classesFor(descriptor: RunContentDescriptor): Set<String> =
        seenClasses[descriptor]?.toSet() ?: emptySet()

    private fun registerCleanup(descriptor: RunContentDescriptor) {
        val cleanup = Disposable { seenClasses.remove(descriptor) }
        if (!Disposer.tryRegister(descriptor, cleanup)) {
            seenClasses.remove(descriptor)
        }
    }
}
