package ai.mwm.client.cloud

import ai.mwm.client.R
import android.content.Context
import android.graphics.*
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.withTimeout

object NotificationArtwork {
    fun logo(context: Context): Bitmap {
        val bitmap=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888)
        context.getDrawable(R.mipmap.ic_launcher)?.let {it.setBounds(0,0,144,144);it.draw(Canvas(bitmap))}
        return bitmap
    }
    private fun initial(name: String): Bitmap {
        val bitmap=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.rgb(250,205,213);canvas.drawCircle(72f,72f,72f,paint)
        paint.color=Color.rgb(91,55,56);paint.textSize=64f;paint.textAlign=Paint.Align.CENTER
        val letter=name.trim().take(1).uppercase().ifBlank {"♡"}
        canvas.drawText(letter,72f,72f-(paint.ascent()+paint.descent())/2,paint)
        return bitmap
    }
    suspend fun partner(context: Context,sender: String): Person {
        val identity=PipoFirebase.get(context).notificationPerson(sender)
        val bitmap=runCatching {
            if(!identity.second.startsWith("cloud:")) null else withTimeout(3500) {
                val bytes=PipoFirebase.get(context).readPhoto(identity.second)
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                val options=BitmapFactory.Options().apply {inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/192).coerceAtLeast(1)}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            }
        }.getOrNull() ?: initial(identity.first)
        return Person.Builder().setKey(sender).setName(identity.first).setIcon(IconCompat.createWithBitmap(bitmap)).build()
    }
    fun fallbackPartner(context:Context):Person {
        val name=ai.mwm.client.data.PipoRepository.get(context).state.value.preferences.partnerName.ifBlank {"Partner"}
        return Person.Builder().setName(name).setIcon(IconCompat.createWithBitmap(initial(name))).build()
    }
    fun decorate(builder: NotificationCompat.Builder,person: Person,context: Context): NotificationCompat.Builder {
        person.icon?.loadDrawable(context)?.let {drawable->
            val bitmap=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,144,144);drawable.draw(Canvas(bitmap));builder.setLargeIcon(bitmap)
        }
        return builder.addPerson(person)
    }
}
