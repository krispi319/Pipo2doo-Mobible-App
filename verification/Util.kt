package android.util
import java.io.File
object Base64 {
 const val NO_WRAP=2;const val DEFAULT=0
 fun encodeToString(b:ByteArray,flags:Int)=java.util.Base64.getEncoder().encodeToString(b)
 fun decode(v:String,flags:Int)=java.util.Base64.getDecoder().decode(v)
}
class AtomicFile(val file:File) {
 fun startWrite()=file.outputStream()
 fun finishWrite(s:java.io.FileOutputStream)=s.close()
 fun failWrite(s:java.io.FileOutputStream)=s.close()
}
