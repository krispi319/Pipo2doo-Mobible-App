import ai.mwm.client.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.app.Application
import android.content.Context
import java.time.LocalDate
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class DailyRepositoryStorageTest {
 private lateinit var c:Context
 private var today=0L
 private val root="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
 private fun fresh():PipoRepository = PipoRepository::class.java.getDeclaredConstructor(Context::class.java).apply {isAccessible=true}.newInstance(c)
 private fun refresh(r:PipoRepository) {PipoRepository::class.java.declaredMethods.first {it.name.startsWith("refreshDaily")}.apply {isAccessible=true}.invoke(r)}
 @Before fun setup(){c=RuntimeEnvironment.getApplication();today=LocalDate.now().toEpochDay();AccountScope.activate(c,"");c.getSharedPreferences(AccountScope.preferencesName(c),0).edit().clear().putString("state","""{"version":4,"tasks":[{"id":"$root","title":"Daily","day":${today-1},"minute":1080,"repeat":"Daily","status":"Todo","reminder":false}],"preferences":{"started":false,"clockZone":"UTC"}}""").commit()}
 @Test fun refreshPersistsDatesAndSeriesAcrossReopening(){val r=fresh();refresh(r);assertEquals(23,r.state.value.tasks.size);val re=fresh();assertEquals(23,re.state.value.tasks.size);assertEquals(root,re.state.value.tasks[1].seriesId)}
 @Test fun completeAndReopenDoNotRemoveOtherDays(){val r=fresh();refresh(r);val child=r.state.value.tasks[1];r.setStatus(child.id,TaskStatus.Done);assertEquals(23,r.state.value.tasks.size);r.setStatus(child.id,TaskStatus.Todo);assertEquals(23,r.state.value.tasks.size);r.setStatus(root,TaskStatus.Done);assertEquals(23,r.state.value.tasks.size)}
 @Test fun deletedOccurrenceStaysDeletedAfterRestart(){val r=fresh();refresh(r);val id=r.state.value.tasks[1].id;r.deleteTask(id);val re=fresh();refresh(re);assertEquals(22,re.state.value.tasks.size);assertTrue(id in re.state.value.skippedDaily);assertFalse(re.state.value.tasks.any {it.id==id})}
 @Test fun deletingRootRetainsExistingDaysAndStopsTemplate(){val r=fresh();refresh(r);r.deleteTask(root);val re=fresh();refresh(re);assertEquals(22,re.state.value.tasks.size);assertTrue(re.state.value.tasks.all {it.seriesId==root && it.parentId!=root})}
}
