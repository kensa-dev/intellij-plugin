package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

object KensaSourceSetResolver {

    fun resolve(element: PsiElement): String? {
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
        return resolveFromModuleName(module)
    }

    fun resolve(project: Project, virtualFile: VirtualFile): String? {
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return null
        return resolveFromModuleName(module)
    }

    fun resolveForClass(project: Project, classFqn: String): String? {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(classFqn, GlobalSearchScope.allScope(project)) ?: return null
        val virtualFile = psiClass.containingFile?.virtualFile ?: return null
        return resolve(project, virtualFile)
    }

    private fun resolveFromModuleName(module: Module): String? {
        val name = module.name
        val sourceSet = name.substringAfterLast('.', missingDelimiterValue = "")
        return when {
            sourceSet.isBlank() -> null
            sourceSet == "main" -> null
            else -> sourceSet
        }
    }
}
