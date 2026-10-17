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
 val disposable=Disposer.newDisposable()
 try {
  val env=KotlinCoreEnvironment.createForProduction(disposable,CompilerConfiguration(),EnvironmentConfigFiles.JVM_CONFIG_FILES)
  var n=0
  args.forEach {p->val file=KtPsiFactory(env.project).createFile(File(p).readText());val errors=PsiTreeUtil.collectElementsOfType(file,PsiErrorElement::class.java);errors.forEach {println("$p: ${it.errorDescription}")};n+=errors.size}
  check(n==0){"$n syntax errors"};println("${args.size} files: zero Kotlin syntax errors")
 } finally {Disposer.dispose(disposable)}
}
