package ai.mwm.client.cloud

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class StartupRecordLoadTest {
    @Test fun smallWorkspaceUsesOneRequest()=runBlocking {
        var requests=0
        val rows=loadRecordPages {requests++;SharedRecordPage(listOf(JSONObject().put("id","task")),"0-0/1")}
        assertEquals(1,requests);assertEquals("task",rows.single().getString("id"))
    }
    @Test fun cappedPagesLoadEveryTaskWithoutEmptyFinalRequest()=runBlocking {
        val offsets=mutableListOf<Int>()
        val rows=loadRecordPages {offset->
            offsets.add(offset)
            val count=minOf(100,250-offset)
            SharedRecordPage((offset until offset+count).map {JSONObject().put("id",it.toString())},"$offset-${offset+count-1}/250")
        }
        assertEquals(listOf(0,100,200),offsets)
        assertEquals(250,rows.size)
        assertEquals("249",rows.last().getString("id"))
    }
}
