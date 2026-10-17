@file:OptIn(org.jetbrains.kotlin.K1Deprecation::class)
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import java.io.File
fun main(args:Array<String>) {
 val d=Disposer.newDisposable()
 try {
  val e=KotlinCoreEnvironment.createForProduction(d,CompilerConfiguration(),EnvironmentConfigFiles.JVM_CONFIG_FILES)
  var errors=0
  args.forEach {p->val f=KtPsiFactory(e.project).createFile(File(p).readText());val found=PsiTreeUtil.collectElementsOfType(f,PsiErrorElement::class.java);found.forEach {println("$p: ${it.errorDescription}")};errors+=found.size}
  check(errors==0){"$errors syntax errors"};println("${args.size} Kotlin files: zero syntax errors")
 } finally {Disposer.dispose(d)}
}
