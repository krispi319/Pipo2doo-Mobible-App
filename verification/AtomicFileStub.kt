package android.util
class AtomicFile(private val file:java.io.File){fun openRead()=file.inputStream();fun startWrite()=java.io.FileOutputStream(file);fun finishWrite(out:java.io.FileOutputStream){out.close()};fun failWrite(out:java.io.FileOutputStream){out.close()}}
