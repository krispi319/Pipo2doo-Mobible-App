package ai.mwm.client.reminders
import android.content.Context
object NotificationSounds {
 fun selected(c:Context)=c.getSharedPreferences("tone",0).getString("tone","system")!!
 fun select(c:Context,t:String){require(t in listOf("system","pi","silent"));c.getSharedPreferences("tone",0).edit().putString("tone",t).commit()}
}
