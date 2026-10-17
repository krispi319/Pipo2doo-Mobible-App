package android.content
import java.io.File
open class Context(val noBackupFilesDir:File,val owner:String="alice") {
 companion object { const val MODE_PRIVATE=0 }
 val stores=mutableMapOf<String,SharedPreferences>()
 fun getSharedPreferences(name:String,mode:Int)=stores.getOrPut(name){SharedPreferences()}
}
class SharedPreferences {
 val values=mutableMapOf<String,Any?>()
 fun contains(k:String)=values.containsKey(k)
 fun getString(k:String,d:String?):String?=values[k] as? String ?: d
 fun getLong(k:String,d:Long)=values[k] as? Long ?: d
 fun edit()=Editor(this)
 class Editor(val p:SharedPreferences) {
  val updates=mutableMapOf<String,Any?>()
  fun putString(k:String,v:String?)=apply{updates[k]=v}
  fun putLong(k:String,v:Long)=apply{updates[k]=v}
  fun commit():Boolean {p.values.putAll(updates);return true}
 }
}
